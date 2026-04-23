package com.eai.trigger;

import com.eai.domain.TriggerType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TriggerConfig {
    public TriggerType type;
    public String cron;
    public String topic;
    public String groupId;
    public String watchPath;
}
