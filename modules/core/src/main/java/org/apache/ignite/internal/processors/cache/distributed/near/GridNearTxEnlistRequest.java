/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.cache.distributed.near;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.internal.GridDirectTransient;
import org.apache.ignite.internal.processors.affinity.AffinityTopologyVersion;
import org.apache.ignite.internal.processors.cache.CacheEntryPredicate;
import org.apache.ignite.internal.processors.cache.CacheObject;
import org.apache.ignite.internal.processors.cache.CacheObjectContext;
import org.apache.ignite.internal.processors.cache.GridCacheContext;
import org.apache.ignite.internal.processors.cache.GridCacheDeployable;
import org.apache.ignite.internal.processors.cache.GridCacheIdMessage;
import org.apache.ignite.internal.processors.cache.GridCacheSharedContext;
import org.apache.ignite.internal.processors.cache.KeyCacheObject;
import org.apache.ignite.internal.processors.cache.distributed.dht.GridInvokeValue;
import org.apache.ignite.internal.processors.cache.mvcc.MvccSnapshot;
import org.apache.ignite.internal.processors.cache.version.GridCacheVersion;
import org.apache.ignite.internal.processors.query.EnlistOperation;
import org.apache.ignite.internal.util.tostring.GridToStringExclude;
import org.apache.ignite.internal.util.typedef.internal.S;
import org.apache.ignite.lang.IgniteBiTuple;
import org.apache.ignite.lang.IgniteUuid;
import org.apache.ignite.plugin.extensions.communication.Message;
import org.apache.ignite.plugin.extensions.communication.MessageCollectionItemType;
import org.apache.ignite.plugin.extensions.communication.MessageReader;
import org.apache.ignite.plugin.extensions.communication.MessageWriter;
import org.jetbrains.annotations.Nullable;

/**
 * Request to enlist into transaction and acquire locks for entries produced with Cache API operations.
 *
 * One request per batch of entries is used.
 */
public class GridNearTxEnlistRequest extends GridCacheIdMessage implements GridCacheDeployable {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    private long threadId;

    /** Future id. */
    private IgniteUuid futId;

    /** */
    private boolean clientFirst;

    /** */
    private int miniId;

    /** */
    private AffinityTopologyVersion topVer;

    /** */
    private GridCacheVersion lockVer;

    /** Mvcc snapshot. */
    private MvccSnapshot mvccSnapshot;

    /** */
    private long timeout;

    /** */
    private long txTimeout;

    /** */
    private int taskNameHash;

    /** Rows to enlist. */
    @GridDirectTransient
    private Collection<Object> rows;

    /** Serialized rows keys. */
    @GridToStringExclude
    private KeyCacheObject[] keys;

    /** Serialized rows values. */
    @GridToStringExclude
    private Message[] values;

    /** Enlist operation. */
    private EnlistOperation op;

    /** Keep binary flag. */
    private boolean keepBinary;

    /** Filter. */
    @GridToStringExclude
    private CacheEntryPredicate filter;

    /** Need previous value flag. */
    private boolean needRes;

    /**
     * Default constructor.
     */
    public GridNearTxEnlistRequest() {
        // No-op.
    }

    /**
     * Constructor.
     *
     * @param cacheId Cache id.
     * @param threadId Thread id.
     * @param futId Future id.
     * @param miniId Mini-future id.
     * @param topVer Topology version.
     * @param lockVer Lock version.
     * @param mvccSnapshot Mvcc snapshot.
     * @param clientFirst First client request flag.
     * @param timeout Timeout.
     * @param txTimeout Tx timeout.
     * @param taskNameHash Task name hash.
     * @param rows Rows.
     * @param op Operation.
     * @param filter Filter.
     */
    GridNearTxEnlistRequest(int cacheId,
        long threadId,
        IgniteUuid futId,
        int miniId,
        AffinityTopologyVersion topVer,
        GridCacheVersion lockVer,
        MvccSnapshot mvccSnapshot,
        boolean clientFirst,
        long timeout,
        long txTimeout,
        int taskNameHash,
        Collection<Object> rows,
        EnlistOperation op,
        boolean needRes,
        boolean keepBinary,
        @Nullable CacheEntryPredicate filter) {
        this.txTimeout = txTimeout;
        this.keepBinary = keepBinary;
        this.filter = filter;
        this.cacheId = cacheId;
        this.threadId = threadId;
        this.futId = futId;
        this.miniId = miniId;
        this.topVer = topVer;
        this.lockVer = lockVer;
        this.mvccSnapshot = mvccSnapshot;
        this.clientFirst = clientFirst;
        this.timeout = timeout;
        this.taskNameHash = taskNameHash;
        this.rows = rows;
        this.op = op;
        this.needRes = needRes;
    }

    /**
     * @return Thread id.
     */
    public long threadId() {
        return threadId;
    }

    /**
     * @return Future id.
     */
    public IgniteUuid futureId() {
        return futId;
    }

    /**
     * @return Mini future ID.
     */
    public int miniId() {
        return miniId;
    }

    /**
     * @return Topology version.
     */
    @Override public AffinityTopologyVersion topologyVersion() {
        return topVer;
    }

    /**
     * @return Lock version.
     */
    public GridCacheVersion version() {
        return lockVer;
    }

    /**
     * @return MVCC snapshot.
     */
    public MvccSnapshot mvccSnapshot() {
        return mvccSnapshot;
    }

    /**
     * @return Timeout milliseconds.
     */
    public long timeout() {
        return timeout;
    }

    /**
     * @return Tx timeout milliseconds.
     */
    public long txTimeout() {
        return txTimeout;
    }

    /**
     * @return Task name hash.
     */
    public int taskNameHash() {
        return taskNameHash;
    }

    /**
     * @return {@code True} if this is the first client request.
     */
    public boolean firstClientRequest() {
        return clientFirst;
    }

    /**
     * @return Collection of rows.
     */
    public Collection<Object> rows() {
        return rows;
    }

    /**
     * @return Operation.
     */
    public EnlistOperation operation() {
        return op;
    }

    /**
     * @return Need result flag.
     */
    public boolean needRes() {
        return needRes;
    }

    /**
     * @return Keep binary flag.
     */
    public boolean keepBinary() {
        return keepBinary;
    }

    /**
     * @return Filter.
     */
    public CacheEntryPredicate filter() {
        return filter;
    }

    /** {@inheritDoc} */
    @Override public void prepareMarshal(GridCacheSharedContext ctx) throws IgniteCheckedException {
        super.prepareMarshal(ctx);

        GridCacheContext cctx = ctx.cacheContext(cacheId);
        CacheObjectContext objCtx = cctx.cacheObjectContext();

        if (rows != null && keys == null) {
            if (!addDepInfo && ctx.deploymentEnabled())
                addDepInfo = true;

            keys = new KeyCacheObject[rows.size()];

            int i = 0;

            boolean keysOnly = op.isDeleteOrLock();

            values = keysOnly ? null : new Message[keys.length];

            for (Object row : rows) {
                Object key, val = null;

                if (keysOnly)
                    key = row;
                else {
                    key = ((IgniteBiTuple)row).getKey();
                    val = ((IgniteBiTuple)row).getValue();
                }

                assert key != null && (keysOnly || val != null) : "key=" + key + ", val=" + val;

                KeyCacheObject key0 = cctx.toCacheKeyObject(key);

                assert key0 != null;

                key0.prepareMarshal(objCtx);

                keys[i] = key0;

                if (!keysOnly) {
                    if (op.isInvoke()) {
                        GridInvokeValue val0 = (GridInvokeValue)val;

                        prepareInvokeValue(cctx, val0);

                        values[i] = val0;
                    }
                    else {
                        if (addDepInfo)
                            prepareObject(val, cctx);

                        CacheObject val0 = cctx.toCacheObject(val);

                        assert val0 != null;

                        val0.prepareMarshal(objCtx);

                        values[i] = val0;
                    }
                }

                i++;
            }
        }

        if (filter != null)
            filter.prepareMarshal(cctx);
    }

    /**
     *
     * @param cctx Cache context.
     * @param val0 Invoke value.
     * @throws IgniteCheckedException If failed.
     */
    private void prepareInvokeValue(GridCacheContext cctx, GridInvokeValue val0) throws IgniteCheckedException {
        assert val0 != null && addDepInfo;

        prepareObject(val0.entryProcessor(), cctx.shared());

        for (Object o : val0.invokeArgs())
            prepareObject(o, cctx.shared());

        val0.prepareMarshal(cctx);
    }

    /** {@inheritDoc} */
    @Override public void finishUnmarshal(GridCacheSharedContext ctx, ClassLoader ldr) throws IgniteCheckedException {
        super.finishUnmarshal(ctx, ldr);

        if (keys != null) {
            rows = new ArrayList<>(keys.length);

            CacheObjectContext objCtx = ctx.cacheContext(cacheId).cacheObjectContext();

            for (int i = 0; i < keys.length; i++) {
                keys[i].finishUnmarshal(objCtx, ldr);

                if (op.isDeleteOrLock())
                    rows.add(keys[i]);
                else {
                    if (values[i] != null) {
                        if (op.isInvoke())
                            ((GridInvokeValue)values[i]).finishUnmarshal(ctx, ldr);
                        else
                            ((CacheObject)values[i]).finishUnmarshal(objCtx, ldr);
                    }

                    rows.add(new IgniteBiTuple<>(keys[i], values[i]));
                }
            }

            keys = null;
            values = null;
        }

        if (filter != null)
            filter.finishUnmarshal(ctx.cacheContext(cacheId), ldr);
    }

    /** {@inheritDoc} */
    @Override public boolean writeTo(ByteBuffer buf, MessageWriter writer) {
        writer.setBuffer(buf);

        if (!super.writeTo(buf, writer))
            return false;

        if (!writer.isHeaderWritten()) {
            if (!writer.writeHeader(directType(), fieldsCount()))
                return false;

            writer.onHeaderWritten();
        }

        switch (writer.state()) {
            case 4:
                if (!writer.writeBoolean("clientFirst", clientFirst))
                    return false;

                writer.incrementState();

            case 5:
                if (!writer.writeMessage("filter", filter))
                    return false;

                writer.incrementState();

            case 6:
                if (!writer.writeIgniteUuid("futId", futId))
                    return false;

                writer.incrementState();

            case 7:
                if (!writer.writeBoolean("keepBinary", keepBinary))
                    return false;

                writer.incrementState();

            case 8:
                if (!writer.writeObjectArray("keys", keys, MessageCollectionItemType.MSG))
                    return false;

                writer.incrementState();

            case 9:
                if (!writer.writeMessage("lockVer", lockVer))
                    return false;

                writer.incrementState();

            case 10:
                if (!writer.writeInt("miniId", miniId))
                    return false;

                writer.incrementState();

            case 11:
                if (!writer.writeMessage("mvccSnapshot", mvccSnapshot))
                    return false;

                writer.incrementState();

            case 12:
                if (!writer.writeBoolean("needRes", needRes))
                    return false;

                writer.incrementState();

            case 13:
                if (!writer.writeByte("op", op != null ? (byte)op.ordinal() : -1))
                    return false;

                writer.incrementState();

            case 14:
                if (!writer.writeInt("taskNameHash", taskNameHash))
                    return false;

                writer.incrementState();

            case 15:
                if (!writer.writeLong("threadId", threadId))
                    return false;

                writer.incrementState();

            case 16:
                if (!writer.writeLong("timeout", timeout))
                    return false;

                writer.incrementState();

            case 17:
                if (!writer.writeAffinityTopologyVersion("topVer", topVer))
                    return false;

                writer.incrementState();

            case 18:
                if (!writer.writeLong("txTimeout", txTimeout))
                    return false;

                writer.incrementState();

            case 19:
                if (!writer.writeObjectArray("values", values, MessageCollectionItemType.MSG))
                    return false;

                writer.incrementState();

        }

        return true;
    }

    /** {@inheritDoc} */
    @Override public boolean readFrom(ByteBuffer buf, MessageReader reader) {
        reader.setBuffer(buf);

        if (!reader.beforeMessageRead())
            return false;

        if (!super.readFrom(buf, reader))
            return false;

        switch (reader.state()) {
            case 4:
                clientFirst = reader.readBoolean("clientFirst");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 5:
                filter = reader.readMessage("filter");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 6:
                futId = reader.readIgniteUuid("futId");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 7:
                keepBinary = reader.readBoolean("keepBinary");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 8:
                keys = reader.readObjectArray("keys", MessageCollectionItemType.MSG, KeyCacheObject.class);

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 9:
                lockVer = reader.readMessage("lockVer");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 10:
                miniId = reader.readInt("miniId");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 11:
                mvccSnapshot = reader.readMessage("mvccSnapshot");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 12:
                needRes = reader.readBoolean("needRes");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 13:
                byte opOrd;

                opOrd = reader.readByte("op");

                if (!reader.isLastRead())
                    return false;

                op = EnlistOperation.fromOrdinal(opOrd);

                reader.incrementState();

            case 14:
                taskNameHash = reader.readInt("taskNameHash");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 15:
                threadId = reader.readLong("threadId");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 16:
                timeout = reader.readLong("timeout");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 17:
                topVer = reader.readAffinityTopologyVersion("topVer");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 18:
                txTimeout = reader.readLong("txTimeout");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 19:
                values = reader.readObjectArray("values", MessageCollectionItemType.MSG, Message.class);

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

        }

        return reader.afterMessageRead(GridNearTxEnlistRequest.class);
    }

    /** {@inheritDoc} */
    @Override public byte fieldsCount() {
        return 20;
    }

    /** {@inheritDoc} */
    @Override public boolean addDeploymentInfo() {
        return addDepInfo;
    }

    /** {@inheritDoc} */
    @Override public short directType() {
        return 159;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNearTxEnlistRequest.class, this);
    }
}
