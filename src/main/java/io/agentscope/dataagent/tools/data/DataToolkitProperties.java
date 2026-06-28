/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.agentscope.dataagent.tools.data;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration model for admin-registered JDBC data sources. */
@ConfigurationProperties(prefix = "dataagent.data")
public class DataToolkitProperties {

    private List<RegisteredDataSourceProperties> sources = new ArrayList<>();

    public List<RegisteredDataSourceProperties> getSources() {
        return sources;
    }

    public void setSources(List<RegisteredDataSourceProperties> sources) {
        this.sources = sources == null ? new ArrayList<>() : sources;
    }

    public static class RegisteredDataSourceProperties {

        private String id;
        private String label;
        private String description;
        private String kind = "jdbc";
        private String jdbcUrl;
        private String driverClassName;
        private String username;
        private String password;
        private int previewRowLimit = 50;
        private int sampleRowLimit = 5;
        private List<String> tags = new ArrayList<>();
        private SemanticProperties semantic = new SemanticProperties();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public void setJdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getPreviewRowLimit() {
            return previewRowLimit;
        }

        public void setPreviewRowLimit(int previewRowLimit) {
            this.previewRowLimit = previewRowLimit;
        }

        public int getSampleRowLimit() {
            return sampleRowLimit;
        }

        public void setSampleRowLimit(int sampleRowLimit) {
            this.sampleRowLimit = sampleRowLimit;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags == null ? new ArrayList<>() : tags;
        }

        public SemanticProperties getSemantic() {
            return semantic;
        }

        public void setSemantic(SemanticProperties semantic) {
            this.semantic = semantic == null ? new SemanticProperties() : semantic;
        }
    }

    public static class SemanticProperties {

        private String orders;
        private String orderItems;
        private String users;
        private String products;
        private String refunds;
        private String timeColumn;
        private List<String> metrics = new ArrayList<>();
        private List<String> sensitiveColumns = new ArrayList<>();

        public String getOrders() {
            return orders;
        }

        public void setOrders(String orders) {
            this.orders = orders;
        }

        public String getOrderItems() {
            return orderItems;
        }

        public void setOrderItems(String orderItems) {
            this.orderItems = orderItems;
        }

        public String getUsers() {
            return users;
        }

        public void setUsers(String users) {
            this.users = users;
        }

        public String getProducts() {
            return products;
        }

        public void setProducts(String products) {
            this.products = products;
        }

        public String getRefunds() {
            return refunds;
        }

        public void setRefunds(String refunds) {
            this.refunds = refunds;
        }

        public String getTimeColumn() {
            return timeColumn;
        }

        public void setTimeColumn(String timeColumn) {
            this.timeColumn = timeColumn;
        }

        public List<String> getMetrics() {
            return metrics;
        }

        public void setMetrics(List<String> metrics) {
            this.metrics = metrics == null ? new ArrayList<>() : metrics;
        }

        public List<String> getSensitiveColumns() {
            return sensitiveColumns;
        }

        public void setSensitiveColumns(List<String> sensitiveColumns) {
            this.sensitiveColumns =
                    sensitiveColumns == null ? new ArrayList<>() : sensitiveColumns;
        }
    }
}
