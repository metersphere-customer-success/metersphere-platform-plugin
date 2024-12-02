package io.metersphere.platform.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

@Getter
@Setter
public class GetCreateMetaDataResponse extends ZentaoResponse {

    @Getter
    @Setter
    public static class MetaData {
        private String title;
        private Map users;
        //private Map customFields;
        private Map bug;
        private LinkedList<Map<String, Object>> builds;
    }
}
