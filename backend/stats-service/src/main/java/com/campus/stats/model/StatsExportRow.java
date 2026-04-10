package com.campus.stats.model;

import com.alibaba.excel.annotation.ExcelProperty;

public class StatsExportRow {

    @ExcelProperty("Section")
    private String section;

    @ExcelProperty("Metric")
    private String metric;

    @ExcelProperty("Dimension")
    private String dimension;

    @ExcelProperty("Value")
    private String value;

    public StatsExportRow() {
    }

    public StatsExportRow(String section, String metric, String dimension, String value) {
        this.section = section;
        this.metric = metric;
        this.dimension = dimension;
        this.value = value;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public String getDimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}

