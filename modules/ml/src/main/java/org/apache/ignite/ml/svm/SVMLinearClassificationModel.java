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

package org.apache.ignite.ml.svm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ignite.ml.Exportable;
import org.apache.ignite.ml.Exporter;
import org.apache.ignite.ml.IgniteModel;
import org.apache.ignite.ml.inference.json.JSONModel;
import org.apache.ignite.ml.inference.json.JSONWritable;
import org.apache.ignite.ml.math.primitives.vector.Vector;
import org.apache.ignite.ml.math.primitives.vector.VectorUtils;

/**
 * Base class for SVM linear classification model.
 */
public final class SVMLinearClassificationModel implements IgniteModel<Vector, Double>, Exportable<SVMLinearClassificationModel>,
    JSONWritable {
    /** */
    private static final long serialVersionUID = -996984622291440226L;

    /** Output label format. '0' and '1' for false value and raw distances from the separating hyperplane otherwise. */
    private boolean isKeepingRawLabels;

    /** Threshold to assign '1' label to the observation if raw value more than this threshold. */
    private double threshold = 0.5;

    /** Multiplier of the objects's vector required to make prediction. */
    private Vector weights;

    /** Intercept of the linear regression model. */
    private double intercept;

    /** */
    public SVMLinearClassificationModel() {
    }

    /** */
    public SVMLinearClassificationModel(Vector weights, double intercept) {
        this.weights = weights;
        this.intercept = intercept;
    }

    /**
     * Set up the output label format.
     *
     * @param isKeepingRawLabels The parameter value.
     * @return Model with new isKeepingRawLabels parameter value.
     */
    public SVMLinearClassificationModel withRawLabels(boolean isKeepingRawLabels) {
        this.isKeepingRawLabels = isKeepingRawLabels;
        return this;
    }

    /**
     * Set up the threshold.
     *
     * @param threshold The parameter value.
     * @return Model with new threshold parameter value.
     */
    public SVMLinearClassificationModel withThreshold(double threshold) {
        this.threshold = threshold;
        return this;
    }

    /**
     * Set up the weights.
     *
     * @param weights The parameter value.
     * @return Model with new weights parameter value.
     */
    public SVMLinearClassificationModel withWeights(Vector weights) {
        this.weights = weights;
        return this;
    }

    /**
     * Set up the intercept.
     *
     * @param intercept The parameter value.
     * @return Model with new intercept parameter value.
     */
    public SVMLinearClassificationModel withIntercept(double intercept) {
        this.intercept = intercept;
        return this;
    }

    /** {@inheritDoc} */
    @Override public Double predict(Vector input) {
        final double res = input.dot(weights) + intercept;
        if (isKeepingRawLabels)
            return res;
        else
            return res - threshold > 0 ? 1.0 : 0;
    }

    /**
     * Gets the output label format mode.
     *
     * @return The parameter value.
     */
    public boolean isKeepingRawLabels() {
        return isKeepingRawLabels;
    }

    /**
     * Gets the threshold.
     *
     * @return The parameter value.
     */
    public double threshold() {
        return threshold;
    }

    /**
     * Gets the weights.
     *
     * @return The parameter value.
     */
    public Vector weights() {
        return weights;
    }

    /**
     * Gets the intercept.
     *
     * @return The parameter value.
     */
    public double intercept() {
        return intercept;
    }

    /** {@inheritDoc} */
    @Override public <P> void saveModel(Exporter<SVMLinearClassificationModel, P> exporter, P path) {
        exporter.save(this, path);
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        SVMLinearClassificationModel mdl = (SVMLinearClassificationModel)o;

        return Double.compare(mdl.intercept, intercept) == 0
            && Double.compare(mdl.threshold, threshold) == 0
            && Boolean.compare(mdl.isKeepingRawLabels, isKeepingRawLabels) == 0
            && Objects.equals(weights, mdl.weights);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(weights, intercept, isKeepingRawLabels, threshold);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        if (weights.size() < 20) {
            StringBuilder builder = new StringBuilder();

            for (int i = 0; i < weights.size(); i++) {
                double nextItem = i == weights.size() - 1 ? intercept : weights.get(i + 1);

                builder.append(String.format("%.4f", Math.abs(weights.get(i))))
                    .append("*x")
                    .append(i)
                    .append(nextItem > 0 ? " + " : " - ");
            }

            builder.append(String.format("%.4f", Math.abs(intercept)));
            return builder.toString();
        }

        return "SVMModel [" +
            "weights=" + weights +
            ", intercept=" + intercept +
            ']';
    }

    /** {@inheritDoc} */
    @Override public String toString(boolean pretty) {
        return toString();
    }

    /** Loads SVMLinearClassificationModel from JSON file. */
    public static SVMLinearClassificationModel fromJSON(Path path) {
            ObjectMapper mapper = new ObjectMapper();

            SVMLinearClassificationJSONExportModel exportModel;
            try {
                exportModel = mapper
                        .readValue(new File(path.toAbsolutePath().toString()), SVMLinearClassificationJSONExportModel.class);

                return exportModel.convert();
            } catch (IOException e) {
                e.printStackTrace();
            }

        return null;
    }

    /** {@inheritDoc} */
    @Override public void toJSON(Path path) {
            ObjectMapper mapper = new ObjectMapper();

            try {
                SVMLinearClassificationJSONExportModel exportModel = new SVMLinearClassificationJSONExportModel(
                    System.currentTimeMillis(),
                    "svm_" + UUID.randomUUID().toString(),
                    SVMLinearClassificationModel.class.getSimpleName());
                exportModel.intercept = intercept;
                exportModel.isKeepingRawLabels = isKeepingRawLabels;
                exportModel.threshold = threshold;
                exportModel.weights = weights.asArray();

                File file = new File(path.toAbsolutePath().toString());
                mapper.writeValue(file, exportModel);
            } catch (IOException e) {
                e.printStackTrace();
            }
    }

    /** */
    public static class SVMLinearClassificationJSONExportModel extends JSONModel {
        /**
         * Multiplier of the objects's vector required to make prediction.
         */
        public double[] weights;

        /**
         * Intercept of the linear regression model.
         */
        public double intercept;

        /**
         * Output label format. 0 and 1 for false value and raw sigmoid regression value otherwise.
         */
        public boolean isKeepingRawLabels;

        /**
         * Threshold to assign '1' label to the observation if raw value more than this threshold.
         */
        public double threshold = 0.5;

        /**
         *
         */
        public SVMLinearClassificationJSONExportModel(Long timestamp, String uid, String modelClass) {
            super(timestamp, uid, modelClass);
        }

        /**
         *
         */
        @JsonCreator
        public SVMLinearClassificationJSONExportModel() {
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return "SVMLinearClassificationJSONExportModel{" +
                    "weights=" + Arrays.toString(weights) +
                    ", intercept=" + intercept +
                    ", isKeepingRawLabels=" + isKeepingRawLabels +
                    ", threshold=" + threshold +
                    '}';
        }

        /** {@inheritDoc} */
        @Override public SVMLinearClassificationModel convert() {
            SVMLinearClassificationModel mdl = new SVMLinearClassificationModel();
            mdl.withWeights(VectorUtils.of(weights));
            mdl.withIntercept(intercept);
            mdl.withRawLabels(isKeepingRawLabels);
            mdl.withThreshold(threshold);

            return mdl;
        }
    }
}
