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
 *
 */

package org.apache.ignite.internal.processors.cache.persistence.tree.io;

import org.apache.ignite.internal.pagemem.PageUtils;
import org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMetrics;
import org.apache.ignite.internal.util.GridStringBuilder;

/**
 * IO for partition metadata pages.
 * Persistent partition contains it's own PendingTree.
 */
public class PagePartitionMetaIOV2 extends PagePartitionMetaIO {
    /** */
    private static final int PENDING_TREE_ROOT_OFF = PagePartitionMetaIO.END_OF_PARTITION_PAGE_META;

    /** */
    public static final int PART_META_REUSE_LIST_ROOT_OFF = PENDING_TREE_ROOT_OFF + 8;

    /** */
    protected static final int GAPS_LINK = PART_META_REUSE_LIST_ROOT_OFF + 8;

    /**
     * @param ver Version.
     */
    public PagePartitionMetaIOV2(int ver) {
        super(ver);
    }

    /** {@inheritDoc} */
    @Override public void initNewPage(long pageAddr, long pageId, int pageSize, PageMetrics metrics) {
        super.initNewPage(pageAddr, pageId, pageSize, metrics);

        setPendingTreeRoot(pageAddr, 0L);
        setPartitionMetaStoreReuseListRoot(pageAddr, 0L);
        setGapsLink(pageAddr, 0L);
    }

    /** {@inheritDoc} */
    @Override public long getPendingTreeRoot(long pageAddr) {
        return PageUtils.getLong(pageAddr, PENDING_TREE_ROOT_OFF);
    }

    /** {@inheritDoc} */
    @Override public void setPendingTreeRoot(long pageAddr, long listRoot) {
        PageUtils.putLong(pageAddr, PENDING_TREE_ROOT_OFF, listRoot);
    }

    /**
     * @param pageAddr Page address.
     */
    @Override public long getPartitionMetaStoreReuseListRoot(long pageAddr) {
        return PageUtils.getLong(pageAddr, PART_META_REUSE_LIST_ROOT_OFF);
    }

    /**
     * @param pageAddr Page address.
     * @param listRoot List root.
     */
    @Override public void setPartitionMetaStoreReuseListRoot(long pageAddr, long listRoot) {
        PageUtils.putLong(pageAddr, PART_META_REUSE_LIST_ROOT_OFF, listRoot);
    }

    /**
     * @param pageAddr Page address.
     * @return Partition size.
     */
    @Override public long getGapsLink(long pageAddr) {
        return PageUtils.getLong(pageAddr, GAPS_LINK);
    }

    /**
     * @param pageAddr Page address.
     * @param link Link.
     *
     * @return {@code true} if value has changed as a result of this method's invocation.
     */
    @Override public boolean setGapsLink(long pageAddr, long link) {
        if (getGapsLink(pageAddr) == link)
            return false;

        PageUtils.putLong(pageAddr, GAPS_LINK, link);

        return true;
    }

    /** {@inheritDoc} */
    @Override protected void printFields(long pageAddr, GridStringBuilder sb) {
        super.printFields(pageAddr, sb);

        sb.a(",\n\tpendingTreeRoot=").a(getPendingTreeRoot(pageAddr))
            .a(",\n\tpartitionMetaStoreReuseListRoot=").a(getPartitionMetaStoreReuseListRoot(pageAddr))
            .a(",\n\tcntrUpdDataPageId=").a(getGapsLink(pageAddr));
    }

    /**
     * Upgrade page to PagePartitionMetaIOV2
     *
     * @param pageAddr Page address.
     */
    public void upgradePage(long pageAddr) {
        assert PageIO.getType(pageAddr) == getType();
        assert PageIO.getVersion(pageAddr) < 2;

        PageIO.setVersion(pageAddr, getVersion());
        setPendingTreeRoot(pageAddr, 0);
        setPartitionMetaStoreReuseListRoot(pageAddr, 0);
        setGapsLink(pageAddr, 0);
    }
}
