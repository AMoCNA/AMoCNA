package app.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = SelectedQueryDeserializer.class)
public interface SelectedQuery {

    String getTag();
    String getQueryString();

}
