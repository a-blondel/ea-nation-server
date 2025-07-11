package com.ea.utils;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class AccountUtils {

    /**
     * Generates alternate names
     * @param alts Number of alternate names to provide if duplicate name is found
     * @param name Duplicated name
     * @return
     */
    public static String suggestNames(int alts, String name) {
        Set<String> opts = new LinkedHashSet<>();
        name = name.replaceAll("\"", "");

        if(name.length() > 8) {
            name = name.substring(0, 7);
        }

        for(int i = 1; i <= alts; i++) {
            String suggestion;
            if(i == 1) {
                suggestion = name + "Kid";
            } else if(i == 2) {
                suggestion = name + "Rule";
            } else {
                suggestion = name + ThreadLocalRandom.current().nextInt(1000, 10000);
            }
            if(suggestion.length() > 12) {
                suggestion = suggestion.substring(0, 11); // Limit to 12 characters
            }
            if(suggestion.contains(" ")) {
                suggestion = "\"" + suggestion + "\""; // Add quotes if the name contains spaces
            }
            // Optionally verify in DB if the generated names are already taken, it will be verified again in cper anyway
            opts.add(suggestion);
        }

        return String.join(",", opts);
    }

}
