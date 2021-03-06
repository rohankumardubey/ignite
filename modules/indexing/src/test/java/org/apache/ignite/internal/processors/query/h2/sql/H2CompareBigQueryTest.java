/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.query.h2.sql;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.affinity.AffinityKey;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.cache.query.annotations.QuerySqlField;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.util.typedef.X;
import org.junit.Test;

/**
 * Executes one big query (and subqueries of the big query) to compare query results from h2 database instance and
 * mixed ignite caches (replicated and partitioned) which have the same data models and data content.
 *
 *
 * <pre>
 *
 *  -------------------------------------> rootOrderId (virtual) <--------------------------
 *  |                                                                |                     |
 *  |                                                                |                     |
 *  |                                ---------------------           |                     |
 *  |  ------------------            --part.ReplaceOrder--           |                     |
 *  |  --part.CustOrder--            ---------------------           |                     |
 *  |  ------------------            -  id  PK           -           |                     |
 *  |  -  orderId  PK   - <--  <---> -  orderId          -           |                     |
 *  -- -  rootOrderId   -   |        -  rootOrderId      - -----------                     |
 *     -  origOrderId   -   |        -  refOrderId       -   // = origOrderId              |
 *     -  date          -   |        -  date             -                                 |
 *     -  alias         -   |        -  alias            -                                 |
 *     -  archSeq       -   |        -  archSeq          -          -------------------    |
 *     ------------------   |        ---------------------          ----part.Exec------    |
 *                          |                                       -------------------    |
 *     -----------------    |                                       -  rootOrderId PK - ----
 *     ---part.Cancel---    |                                       -  date           -
 *     -----------------    |        ---------------------          -  execShares int -
 *     -  id       PK  -    |        --part.OrderParams---          -  price      int -
 *     -  refOrderId   - ---|        ---------------------          -  lastMkt    int -
 *     -  date         -    |        -  id  PK           -          -------------------
 *     -----------------    -------  - orderId           -
 *                                   - date              -
 *                                   - parentAlgo        -
 *                                   ---------------------
 *  </pre>
 *
 */
public class H2CompareBigQueryTest extends AbstractH2CompareQueryTest {
    /** Root order count. */
    private static final int ROOT_ORDER_CNT = 1000;

    /** Dates count. */
    private static final int DATES_CNT = 5;

    /** Full the big query. */
    private String bigQry = getBigQry();

    /** Cache cust ord. */
    private static IgniteCache<Integer, CustOrder> cacheCustOrd;

    /** Cache repl ord. */
    private static IgniteCache<Object, ReplaceOrder> cacheReplOrd;

    /** Cache ord parameter. */
    private static IgniteCache<Object, OrderParams> cacheOrdParam;

    /** Cache cancel. */
    private static IgniteCache<Object, Cancel> cacheCancel;

    /** Cache execute. */
    private static IgniteCache<Object, Exec> cacheExec;

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        cfg.setCacheConfiguration(
            cacheConfiguration("custord", CacheMode.PARTITIONED, Integer.class, CustOrder.class),
            cacheConfiguration(
                "replord",
                CacheMode.PARTITIONED,
                useColocatedData() ? AffinityKey.class : Integer.class,
                ReplaceOrder.class
            ),
            cacheConfiguration(
                "ordparam",
                CacheMode.PARTITIONED,
                useColocatedData() ? AffinityKey.class : Integer.class,
                OrderParams.class
            ),
            cacheConfiguration("cancel", CacheMode.PARTITIONED, useColocatedData() ? AffinityKey.class : Integer.class, Cancel.class),
            cacheConfiguration("exec", CacheMode.REPLICATED, useColocatedData() ? AffinityKey.class : Integer.class, Exec.class));

        return cfg;
    }

    /** {@inheritDoc} */
    @Override protected void afterTestsStopped() throws Exception {
        cacheCustOrd = null;
        cacheReplOrd = null;
        cacheOrdParam = null;
        cacheCancel = null;
        cacheExec = null;

        super.afterTestsStopped();
    }

    /**
     * Extracts the big query from file.
     *
     * @return Big query.
     */
    private String getBigQry() {
        String res = "";

        Reader isr = new InputStreamReader(getClass().getResourceAsStream("bigQuery.sql"));

        try (BufferedReader reader = new BufferedReader(isr)) {
            for (String line; (line = reader.readLine()) != null; )
                if (!line.startsWith("--")) // Skip commented lines.
                    res += line + '\n';
        }
        catch (Throwable e) {
            e.printStackTrace();

            fail();
        }

        return res;
    }

    /**
     * @return Use colocated data.
     */
    private boolean useColocatedData() {
        return !distributedJoins();
    }

    /**
     * @return Whehter to use distrubutedJoins or not.
     */
    protected boolean distributedJoins() {
        return false;
    }

    /** {@inheritDoc} */
    @Override protected void createCaches() {
        cacheCustOrd = ignite.cache("custord");
        cacheReplOrd = ignite.cache("replord");
        cacheOrdParam = ignite.cache("ordparam");
        cacheCancel = ignite.cache("cancel");
        cacheExec = ignite.cache("exec");
    }

    /** {@inheritDoc} */
    @Override protected void initCacheAndDbData() throws SQLException {
        final AtomicInteger idGen = new AtomicInteger();

        final Iterable<Integer> rootOrderIds = new ArrayList<Integer>() {{
            for (int i = 0; i < ROOT_ORDER_CNT; i++)
                add(idGen.incrementAndGet());
        }};

        final Date curDate = new Date(new java.util.Date().getTime());

        final List<Date> dates = new ArrayList<Date>() {{
            for (int i = 0; i < DATES_CNT; i++)
                add(new Date(curDate.getTime() - i * 24 * 60 * 60 * 1000)); // Minus i days.
        }};

        final Iterable<CustOrder> orders = new ArrayList<CustOrder>() {{
            for (int rootOrderId : rootOrderIds) {
                // Generate 1 - 5 orders for 1 root order.
                for (int i = 0; i < rootOrderId % 5; i++) {
                    int orderId = idGen.incrementAndGet();

                    CustOrder order = new CustOrder(orderId, rootOrderId, dates.get(orderId % dates.size()),
                        orderId % 2 == 0 ? "CUSTOM" : "OTHER", orderId);

                    add(order);

                    cacheCustOrd.put(order.orderId, order);

                    insertInDb(order);
                }
            }
        }};

        final Collection<OrderParams> params = new ArrayList<OrderParams>() {{
            for (CustOrder o : orders) {
                OrderParams op = new OrderParams(idGen.incrementAndGet(), o.orderId, o.date,
                    o.orderId % 2 == 0 ? "Algo 1" : "Algo 2");

                add(op);

                cacheOrdParam.put(op.key(useColocatedData()), op);

                insertInDb(op);
            }
        }};

        final Collection<ReplaceOrder> replaces = new ArrayList<ReplaceOrder>() {{
            for (CustOrder o : orders) {
                if (o.orderId % 7 == 0) {
                    ReplaceOrder replace = new ReplaceOrder(idGen.incrementAndGet(), o.orderId, o.rootOrderId, o.alias,
                        new Date(o.date.getTime() + 12 * 60 * 60 * 1000), o.orderId); // Plus a half of day.

                    add(replace);

                    cacheReplOrd.put(replace.key(useColocatedData()), replace);

                    insertInDb(replace);
                }
            }
        }};

        final Collection<Cancel> cancels = new ArrayList<Cancel>() {{
            for (CustOrder o : orders) {
                if (o.orderId % 9 == 0) {
                    Cancel c = new Cancel(idGen.incrementAndGet(), o.orderId,
                        new Date(o.date.getTime() + 12 * 60 * 60 * 1000)); // Plus a half of day.

                    add(c);

                    cacheCancel.put(c.key(useColocatedData()), c);

                    insertInDb(c);
                }
            }
        }};

        final Collection<Exec> execs = new ArrayList<Exec>() {{
            for (int rootOrderId : rootOrderIds) {
                int execShares = 10000 + rootOrderId;
                int price = 1000 + rootOrderId;
                int latsMkt = 3000 + rootOrderId;

                Exec exec = new Exec(idGen.incrementAndGet(), rootOrderId,
                    dates.get(rootOrderId % dates.size()), execShares, price, latsMkt);

                add(exec);

                cacheExec.put(exec.key(useColocatedData()), exec);

                insertInDb(exec);
            }
        }};
    }

    /**
     * @throws Exception If failed.
     */
    @Override protected void checkAllDataEquals() throws Exception {
        compareQueryRes0(cacheCustOrd, "select _key, _val, date, orderId, rootOrderId, alias, archSeq, origOrderId " +
            "from \"custord\".CustOrder");
        compareQueryRes0(cacheReplOrd, "select _key, _val, id, date, orderId, rootOrderId, alias, archSeq, refOrderId " +
            "from \"replord\".ReplaceOrder");
        compareQueryRes0(cacheOrdParam, "select _key, _val, id, date, orderId, parentAlgo from \"ordparam\".OrderParams\n");
        compareQueryRes0(cacheCancel, "select _key, _val, id, date, refOrderId from \"cancel\".Cancel\n");
        compareQueryRes0(cacheExec, "select _key, _val, date, rootOrderId, execShares, price, lastMkt from \"exec\".Exec\n");
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testBigQuery() throws Exception {
        X.println();
        X.println(bigQry);
        X.println();

        X.println("   Plan: \n" + cacheCustOrd.query(new SqlFieldsQuery("EXPLAIN " + bigQry)
            .setDistributedJoins(distributedJoins())).getAll());

        List<List<?>> res = compareQueryRes0(cacheCustOrd, bigQry, distributedJoins(), new Object[0], Ordering.RANDOM);

        X.println("   Result size: " + res.size());

        assertTrue(!res.isEmpty()); // Ensure we set good testing data at database.
    }

    /** {@inheritDoc} */
    @Override protected Statement initializeH2Schema() throws SQLException {
        Statement st = super.initializeH2Schema();

        st.execute("CREATE SCHEMA \"custord\"");
        st.execute("CREATE SCHEMA \"replord\"");
        st.execute("CREATE SCHEMA \"ordparam\"");
        st.execute("CREATE SCHEMA \"cancel\"");
        st.execute("CREATE SCHEMA \"exec\"");

        final String keyType = useColocatedData() ? "other" : "int";

        st.execute("create table \"custord\".CustOrder" +
            "  (" +
            "  _key int not null," +
            "  _val other not null," +
            "  orderId int unique," +
            "  rootOrderId int," +
            "  origOrderId int," +
            "  archSeq int," +
            "  date Date, " +
            "  alias varchar(255)" +
            "  )");

        st.execute("create table \"replord\".ReplaceOrder" +
            "  (" +
            "  _key " + keyType + " not null," +
            "  _val other not null," +
            "  id int unique," +
            "  orderId int ," +
            "  rootOrderId int," +
            "  refOrderId int," +
            "  archSeq int," +
            "  date Date, " +
            "  alias varchar(255)" +
            "  )");

        st.execute("create table \"ordparam\".OrderParams" +
            "  (" +
            "  _key " + keyType + " not null," +
            "  _val other not null," +
            "  id int unique," +
            "  orderId int ," +
            "  date Date, " +
            "  parentAlgo varchar(255)" +
            "  )");

        st.execute("create table \"cancel\".Cancel" +
            "  (" +
            "  _key " + keyType + " not null," +
            "  _val other not null," +
            "  id int unique," +
            "  date Date, " +
            "  refOrderId int" +
            "  )");

        st.execute("create table \"exec\".Exec" +
            "  (" +
            "  _key " + keyType + " not null," +
            "  _val other not null," +
            "  rootOrderId int unique," +
            "  date Date, " +
            "  execShares int," +
            "  price int," +
            "  lastMkt int" +
            "  )");

        conn.commit();

        return st;
    }

    /**
     * Insert {@link CustOrder} at h2 database.
     *
     * @param o CustOrder.
     */
    private void insertInDb(CustOrder o) throws SQLException {
        try (PreparedStatement st = conn.prepareStatement(
            "insert into \"custord\".CustOrder (_key, _val, orderId, rootOrderId, date, alias, archSeq, origOrderId) " +
                "values(?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;

            st.setObject(++i, o.orderId);
            st.setObject(++i, o);
            st.setObject(++i, o.orderId);
            st.setObject(++i, o.rootOrderId);
            st.setObject(++i, o.date);
            st.setObject(++i, o.alias);
            st.setObject(++i, o.archSeq);
            st.setObject(++i, o.origOrderId);

            st.executeUpdate();
        }
    }

    /**
     * Insert {@link ReplaceOrder} at h2 database.
     *
     * @param o ReplaceOrder.
     */
    private void insertInDb(ReplaceOrder o) throws SQLException {
        try (PreparedStatement st = conn.prepareStatement(
            "insert into \"replord\".ReplaceOrder (_key, _val, id, orderId, rootOrderId, date, alias, archSeq, refOrderId) " +
                "values(?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;

            st.setObject(++i, o.key(useColocatedData()));
            st.setObject(++i, o);
            st.setObject(++i, o.id);
            st.setObject(++i, o.orderId);
            st.setObject(++i, o.rootOrderId);
            st.setObject(++i, o.date);
            st.setObject(++i, o.alias);
            st.setObject(++i, o.archSeq);
            st.setObject(++i, o.refOrderId);

            st.executeUpdate();
        }
    }

    /**
     * Insert {@link OrderParams} at h2 database.
     *
     * @param o OrderParams.
     */
    private void insertInDb(OrderParams o) throws SQLException {
        try (PreparedStatement st = conn.prepareStatement(
            "insert into \"ordparam\".OrderParams (_key, _val, id, date, orderId, parentAlgo) values(?, ?, ?, ?, ?, ?)")) {
            int i = 0;

            st.setObject(++i, o.key(useColocatedData()));
            st.setObject(++i, o);
            st.setObject(++i, o.id);
            st.setObject(++i, o.date);
            st.setObject(++i, o.orderId);
            st.setObject(++i, o.parentAlgo);

            st.executeUpdate();
        }
    }

    /**
     * Insert {@link Cancel} at h2 database.
     *
     * @param o Cancel.
     */
    private void insertInDb(Cancel o) throws SQLException {
        try (PreparedStatement st = conn.prepareStatement(
            "insert into \"cancel\".Cancel (_key, _val, id, date, refOrderId) values(?, ?, ?, ?, ?)")) {
            int i = 0;

            st.setObject(++i, o.key(useColocatedData()));
            st.setObject(++i, o);
            st.setObject(++i, o.id);
            st.setObject(++i, o.date);
            st.setObject(++i, o.refOrderId);

            st.executeUpdate();
        }
    }

    /**
     * Insert {@link Exec} at h2 database.
     *
     * @param o Execution.
     */
    private void insertInDb(Exec o) throws SQLException {
        try (PreparedStatement st = conn.prepareStatement(
            "insert into \"exec\".Exec (_key, _val, date, rootOrderId, execShares, price, lastMkt) " +
                "values(?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;

            st.setObject(++i, o.key(useColocatedData()));
            st.setObject(++i, o);
            st.setObject(++i, o.date);
            st.setObject(++i, o.rootOrderId);
            st.setObject(++i, o.execShares);
            st.setObject(++i, o.price);
            st.setObject(++i, o.lastMkt);

            st.executeUpdate();
        }
    }

    /**
     * Custom Order.
     */
    static class CustOrder implements Serializable {
        /** Primary key. */
        @QuerySqlField(index = true)
        private int orderId;

        /** Root order ID*/
        @QuerySqlField
        private int rootOrderId;

        /** Orig order ID*/
        @QuerySqlField
        private int origOrderId;

        /** Date */
        @QuerySqlField
        private Date date;

        /**  */
        @QuerySqlField
        private String alias = "CUSTOM";

        /**  */
        @QuerySqlField
        private int archSeq = 11; // TODO: use it.

        /**
         * @param orderId Order id.
         * @param rootOrderId Root order id.
         * @param date Date.
         * @param alias Alias.
         * @param origOrderId Orig order id.
         */
        CustOrder(int orderId, int rootOrderId, Date date, String alias, int origOrderId) {
            this.orderId = orderId;
            this.rootOrderId = rootOrderId;
            this.origOrderId = origOrderId;
            this.date = date;
            this.alias = alias;
        }

        /** {@inheritDoc} */
        @Override public boolean equals(Object o) {
            return this == o || o instanceof CustOrder && orderId == ((CustOrder)o).orderId;
        }

        /** {@inheritDoc} */
        @Override public int hashCode() {
            return orderId;
        }
    }

    /**
     * Replace Order.
     */
    static class ReplaceOrder implements Serializable {
        /** Primary key. */
        @QuerySqlField(index = true)
        private int id;

        /** Order id. */
        @QuerySqlField(index = true)
        private int orderId;

        /** Root order ID*/
        @QuerySqlField
        private int rootOrderId;

        /** Ref order ID*/
        @QuerySqlField
        private int refOrderId;

        /** Date */
        @QuerySqlField
        private Date date;

        /**  */
        @QuerySqlField
        private String alias = "CUSTOM";

        /**  */
        @QuerySqlField
        private int archSeq = 111; // TODO: use it.

        /**
         * @param id Id.
         * @param orderId Order id.
         * @param rootOrderId Root order id.
         * @param alias Alias.
         * @param date Date.
         * @param refOrderId Reference order id.
         */
        ReplaceOrder(int id, int orderId, int rootOrderId, String alias, Date date, int refOrderId) {
            this.id = id;
            this.orderId = orderId;
            this.rootOrderId = rootOrderId;
            this.refOrderId = refOrderId;
            this.date = date;
            this.alias = alias;
        }

        /**
         * @param useColocatedData Use colocated data.
         * @return Key.
         */
        public Object key(boolean useColocatedData) {
            return useColocatedData ? new AffinityKey<>(id, orderId) : id;
        }

        /** {@inheritDoc} */
        @Override public boolean equals(Object o) {
            return this == o || o instanceof ReplaceOrder && id == ((ReplaceOrder)o).id;
        }

        /** {@inheritDoc} */
        @Override public int hashCode() {
            return id;
        }
    }

    /**
     * Order params.
     */
    static class OrderParams implements Serializable {
        /** Primary key. */
        @QuerySqlField(index = true)
        private int id;

        /** Order id. */
        @QuerySqlField(index = true)
        private int orderId;

        /** Date */
        @QuerySqlField
        private Date date;

        /**  */
        @QuerySqlField
        private String parentAlgo = "CUSTOM_ALGO";

        /**
         * @param id Id.
         * @param orderId Order id.
         * @param date Date.
         * @param parentAlgo Parent algo.
         */
        OrderParams(int id, int orderId, Date date, String parentAlgo) {
            this.id = id;
            this.orderId = orderId;
            this.date = date;
            this.parentAlgo = parentAlgo;
        }

        /**
         * @param useColocatedData Use colocated data.*
         * @return Key.
         */
        public Object key(boolean useColocatedData) {
            return useColocatedData ? new AffinityKey<>(id, orderId) : id;
        }

        /** {@inheritDoc} */
        @Override public boolean equals(Object o) {
            return this == o || o instanceof OrderParams && id == ((OrderParams)o).id;
        }

        /** {@inheritDoc} */
        @Override public int hashCode() {
            return id;
        }
    }

    /**
     * Cancel Order.
     */
    static class Cancel implements Serializable {
        /** Primary key. */
        @QuerySqlField(index = true)
        private int id;

        /** Order id. */
        @QuerySqlField(index = true)
        private int refOrderId;

        /** Date */
        @QuerySqlField
        private Date date;

        /**
         * @param id ID.
         * @param refOrderId Reference order id.
         * @param date Date.
         */
        Cancel(int id, int refOrderId, Date date) {
            this.id = id;
            this.refOrderId = refOrderId;
            this.date = date;
        }

        /**
         * @param useColocatedData Use colocated data.
         * @return Key.
         */
        public Object key(boolean useColocatedData) {
            return useColocatedData ? new AffinityKey<>(id, refOrderId) : id;
        }

        /** {@inheritDoc} */
        @Override public boolean equals(Object o) {
            return this == o || o instanceof Cancel && id == ((Cancel)o).id;
        }

        /** {@inheritDoc} */
        @Override public int hashCode() {
            return id;
        }
    }

    /**
     * Execute information about root query.
     */
    static class Exec implements Serializable {
        /** */
        @QuerySqlField
        private int id;

        /** */
        @QuerySqlField(index = true)
        private int rootOrderId;

        /** Date */
        @QuerySqlField
        private Date date;

        /** */
        @QuerySqlField
        private int execShares;

        /** */
        @QuerySqlField
        private int price;

        /** */
        @QuerySqlField
        private int lastMkt;

        /**
         * @param id ID.
         * @param rootOrderId Root order id.
         * @param date Date.
         * @param execShares Execute shares.
         * @param price Price.
         * @param lastMkt Last mkt.
         */
        Exec(int id, int rootOrderId, Date date, int execShares, int price, int lastMkt) {
            this.id = id;
            this.rootOrderId = rootOrderId;
            this.date = date;
            this.execShares = execShares;
            this.price = price;
            this.lastMkt = lastMkt;
        }

        /** {@inheritDoc} */
        @Override public boolean equals(Object o) {
            return this == o || o instanceof Exec && id == ((Exec)o).id;
        }

        /** {@inheritDoc} */
        @Override public int hashCode() {
            return id;
        }

        /** */
        public Object key(boolean useColocatedData) {
            return useColocatedData ? new AffinityKey<>(id, rootOrderId) : id;
        }
    }
}
