package main.model.promql.simplequeries;

import main.model.metrics.simple.SimpleMetricTemplate;
import main.model.promql.AbstractQueryResult;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * POJO for JSON conversion
 */
public class SimpleQueryResult extends AbstractQueryResult implements Serializable {

    private SimpleData simpleData;

    public SimpleQueryResult() {
        //required by Jackson
    }

    public SimpleData getData() {
        return simpleData;
    }

    public void setData(SimpleData simpleData) {
        this.simpleData = simpleData;
    }

    @Override
    public String toString() {
        return "QueryResult{" +
                "status='" + super.getStatus() + '\'' +
                ", data=" + simpleData +
                '}';
    }

    /**
     * @return Metrics received from query
     */
    @Override
    public ArrayList<SimpleMetricTemplate> getMetricObjects() {
        return this.simpleData.getMetricObjects();
    }

}
