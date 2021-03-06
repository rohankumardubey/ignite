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

package org.apache.ignite.internal.processors.rest.client.message;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;
import org.apache.ignite.internal.util.typedef.internal.S;

/** */
public class GridClientWarmUpRequest extends GridClientNodeStateBeforeStartRequest {
    /** Serial version uid. */
    private static final long serialVersionUID = 0L;

    /** Stop warm-up. */
    private boolean stopWarmUp;

    /**
     * Return {@code true} to stop warm-up.
     *
     * @return {@code true} to stop warm-up.
     */
    public boolean stopWarmUp() {
        return stopWarmUp;
    }

    /**
     * Set need to stop warm-up.
     *
     * @param stopWarmUp {@code true} to stop warm-up.
     * @return {@code this} instance.
     */
    public GridClientWarmUpRequest stopWarmUp(boolean stopWarmUp) {
        this.stopWarmUp = stopWarmUp;

        return this;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeBoolean(stopWarmUp);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        stopWarmUp = in.readBoolean();
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        GridClientWarmUpRequest req = (GridClientWarmUpRequest)o;

        return stopWarmUp == req.stopWarmUp;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(stopWarmUp);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridClientWarmUpRequest.class, this, super.toString());
    }
}
