package com.jnj.kafka.admin.lagalert;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ConsumerGroupFilter {

    private final Set<String> includedConsumerGroups;
    private final Set<String> excludedConsumerGroups;

    public ConsumerGroupFilter(String includedConsumerGroups, String excludedConsumerGroups) {
        this.includedConsumerGroups = getSet(includedConsumerGroups);
        this.excludedConsumerGroups = getSet(excludedConsumerGroups);
    }

    public boolean filter(String groupId) {

        if (includedConsumerGroups.size() == 0 && excludedConsumerGroups.size() == 0) {
            return true;
        }

        // if included list specified, check that group ID starts with one of the prefixes in the included list
        if (includedConsumerGroups.size() != 0) {
            for (String included : includedConsumerGroups) {
                if (groupId.startsWith(included)) {
                    return true;
                }
            }
            return false;
        }

        // if excluded list specified, check that group ID does not start with one of the prefixes in the excluded list
        for (String excluded : excludedConsumerGroups) {
            if (groupId.startsWith(excluded)) {
                return false;
            }
        }
        return true;
    }

    private Set<String> getSet(String str) {
        Set<String> set = new HashSet<>();
        if (str != null && !"".equals(str)) {
            String[] strs = str.split(",");
            set.addAll(Arrays.asList(strs));
        }
        return set;
    }
}
