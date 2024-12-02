package io.metersphere.platform.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.regex.Pattern;

@Getter
@Setter
public class ZentaoJsonApiUrl {
    private String login;
    private String sessionGet;
    private String bugCreate;
    private String createMetaData;
    private String bugUpdate;
    private String bugList;
    private String bugDelete;
    private String bugGet;
    private String storyGet;
    private String userGet;
    private String buildsGet;
    private String buildsGetV17;
    private String fileUpload;
    private String fileDelete;
    private String fileDownload;
    private String replaceImgUrl;
    private String productGet;
    private Pattern imgPattern;
}
