/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.protocol.xpack;

import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Locale;

/**
 * Fetch information about X-Pack from the cluster.
 */
public class XPackInfoRequest extends ActionRequest {

    public enum Category {
        BUILD, LICENSE, FEATURES;

        public static EnumSet<Category> toSet(String... categories) {
            EnumSet<Category> set = EnumSet.noneOf(Category.class);
            for (String category : categories) {
                switch (category) {
                    case "_all":
                        return EnumSet.allOf(Category.class);
                    case "_none":
                        return EnumSet.noneOf(Category.class);
                    default:
                        set.add(Category.valueOf(category.toUpperCase(Locale.ROOT)));
                }
            }
            return set;
        }
    }

    private boolean verbose;
    private EnumSet<Category> categories = EnumSet.noneOf(Category.class);

    public XPackInfoRequest() {}

    public XPackInfoRequest(StreamInput in) throws IOException {
        // NOTE: this does *not* call super, THIS IS A BUG
        this.verbose = in.readBoolean();
        EnumSet<Category> categories = EnumSet.noneOf(Category.class);
        int size = in.readVInt();
        for (int i = 0; i < size; i++) {
            categories.add(Category.valueOf(in.readString()));
        }
        this.categories = categories;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setCategories(EnumSet<Category> categories) {
        this.categories = categories;
    }

    public EnumSet<Category> getCategories() {
        return categories;
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeBoolean(verbose);
        out.writeVInt(categories.size());
        for (Category category : categories) {
            out.writeString(category.name());
        }
    }
}
