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

package org.apache.ignite.mxbean;

/**
 * MBean that controls critical failure handling.
 */
@MXBeanDescription("MBean that controls critical failure handling.")
public interface FailureHandlingMxBean {
    /** @return Whether critical workers liveness checking is enabled. */
    @MXBeanDescription("Enable/disable critical workers liveness checking.")
    public boolean getLivenessCheckEnabled();

    /** @param val Whether critical workers liveness checking is enabled. */
    public void setLivenessCheckEnabled(boolean val);

    /** @return Maximum inactivity period for system worker. Negative value denotes infinite timeout. */
    @MXBeanDescription("Maximum inactivity period for system worker. Critical failure handler fires if exceeded. " +
        "Nonpositive value denotes infinite timeout.")
    public long getSystemWorkerBlockedTimeout();

    /** @param val Maximum inactivity period for system worker. Negative value denotes infinite timeout. */
    public void setSystemWorkerBlockedTimeout(long val);

    /** @return Timeout for checkpoint read lock acquisition. Negative value denotes infinite timeout. */
    @MXBeanDescription("Timeout for checkpoint read lock acquisition. Critical failure handler fires if exceeded. " +
        "Nonpositive value denotes infinite timeout.")
    public long getCheckpointReadLockTimeout();

    /** @param val Timeout for checkpoint read lock acquisition. Negative value denotes infinite timeout. */
    public void setCheckpointReadLockTimeout(long val);
}
