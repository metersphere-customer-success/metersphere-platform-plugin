package io.metersphere.platform.domain;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * @program: metersphere-jira-plugin9.x
 * @ClassName: Jira9XFields
 * @description:
 */
@Data
public class Jira9XField extends JiraCreateMetadataResponse.Field {
    private String fieldId;
    public JiraCreateMetadataResponse.Field toJiraCreateMetadataResponseField(){
        JiraCreateMetadataResponse.Field field = new JiraCreateMetadataResponse.Field();
        field.setName(getName());
        field.setKey(this.fieldId);
        field.setHasDefaultValue(isHasDefaultValue());
//        field.setAllowedValues();
//        field.setSchema(this.schema);
//        field.setDefaultValue();
//        field.setSchema();
        return field;
    }

}
