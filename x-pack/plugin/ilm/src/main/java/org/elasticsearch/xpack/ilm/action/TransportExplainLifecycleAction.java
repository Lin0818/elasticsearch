/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.ilm.action;

import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.master.info.TransportClusterInfoAction;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.DeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.xpack.core.indexlifecycle.ExplainLifecycleRequest;
import org.elasticsearch.xpack.core.indexlifecycle.ExplainLifecycleResponse;
import org.elasticsearch.xpack.core.indexlifecycle.IndexLifecycleExplainResponse;
import org.elasticsearch.xpack.core.indexlifecycle.LifecycleExecutionState;
import org.elasticsearch.xpack.core.indexlifecycle.LifecycleSettings;
import org.elasticsearch.xpack.core.indexlifecycle.PhaseExecutionInfo;
import org.elasticsearch.xpack.core.indexlifecycle.action.ExplainLifecycleAction;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TransportExplainLifecycleAction
        extends TransportClusterInfoAction<ExplainLifecycleRequest, ExplainLifecycleResponse> {

    private final NamedXContentRegistry xContentRegistry;

    @Inject
    public TransportExplainLifecycleAction(TransportService transportService, ClusterService clusterService, ThreadPool threadPool,
                                           ActionFilters actionFilters, IndexNameExpressionResolver indexNameExpressionResolver,
                                           NamedXContentRegistry xContentRegistry) {
        super(ExplainLifecycleAction.NAME, transportService, clusterService, threadPool, actionFilters,
                ExplainLifecycleRequest::new, indexNameExpressionResolver);
        this.xContentRegistry = xContentRegistry;
    }

    @Override
    protected String executor() {
        // very lightweight operation, no need to fork
        return ThreadPool.Names.SAME;
    }

    @Override
    protected ExplainLifecycleResponse read(StreamInput in) throws IOException {
        return new ExplainLifecycleResponse(in);
    }

    @Override
    protected ClusterBlockException checkBlock(ExplainLifecycleRequest request, ClusterState state) {
        return state.blocks().indicesBlockedException(ClusterBlockLevel.METADATA_READ,
                indexNameExpressionResolver.concreteIndexNames(state, request));
    }

    @Override
    protected void doMasterOperation(ExplainLifecycleRequest request, String[] concreteIndices, ClusterState state,
            ActionListener<ExplainLifecycleResponse> listener) {
        Map<String, IndexLifecycleExplainResponse> indexReponses = new HashMap<>();
        for (String index : concreteIndices) {
            IndexMetaData idxMetadata = state.metaData().index(index);
            Settings idxSettings = idxMetadata.getSettings();
            LifecycleExecutionState lifecycleState = LifecycleExecutionState.fromIndexMetadata(idxMetadata);
            String policyName = LifecycleSettings.LIFECYCLE_NAME_SETTING.get(idxSettings);
            String currentPhase = lifecycleState.getPhase();
            String stepInfo = lifecycleState.getStepInfo();
            BytesArray stepInfoBytes = null;
            if (stepInfo != null) {
                stepInfoBytes = new BytesArray(stepInfo);
            }
            // parse existing phase steps from the phase definition in the index settings
            String phaseDef = lifecycleState.getPhaseDefinition();
            PhaseExecutionInfo phaseExecutionInfo = null;
            if (Strings.isNullOrEmpty(phaseDef) == false) {
                try (XContentParser parser = JsonXContent.jsonXContent.createParser(xContentRegistry,
                        DeprecationHandler.THROW_UNSUPPORTED_OPERATION, phaseDef)) {
                    phaseExecutionInfo = PhaseExecutionInfo.parse(parser, currentPhase);
                } catch (IOException e) {
                    listener.onFailure(new ElasticsearchParseException(
                        "failed to parse phase definition for index [" + index + "]", e));
                    return;
                }
            }
            final IndexLifecycleExplainResponse indexResponse;
            if (Strings.hasLength(policyName)) {
                indexResponse = IndexLifecycleExplainResponse.newManagedIndexResponse(index, policyName,
                    lifecycleState.getLifecycleDate(),
                    lifecycleState.getPhase(),
                    lifecycleState.getAction(),
                    lifecycleState.getStep(),
                    lifecycleState.getFailedStep(),
                    lifecycleState.getPhaseTime(),
                    lifecycleState.getActionTime(),
                    lifecycleState.getStepTime(),
                    stepInfoBytes,
                    phaseExecutionInfo);
            } else {
                indexResponse = IndexLifecycleExplainResponse.newUnmanagedIndexResponse(index);
            }
            indexReponses.put(indexResponse.getIndex(), indexResponse);
        }
        listener.onResponse(new ExplainLifecycleResponse(indexReponses));
    }

}
