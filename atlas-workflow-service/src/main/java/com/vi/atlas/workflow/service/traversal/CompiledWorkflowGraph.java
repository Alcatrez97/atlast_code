package com.vi.atlas.workflow.service.traversal;

import com.vi.atlas.common.dto.WorkflowEdgeDto;
import com.vi.atlas.common.dto.WorkflowGraphDto;
import com.vi.atlas.common.dto.WorkflowNodeDto;
import org.springframework.expression.Expression;

import java.util.*;

/**
 * Immutable compiled representation of a {@link WorkflowGraphDto}.
 *
 * <p>All nodes, incoming/outgoing edge indexes, start nodes, and SpEL conditions
 * are pre-computed, pre-indexed, and bytecode-compiled at graph initialization time.
 * This guarantees O(1) pointer navigation and zero object allocation during runtime graph traversal.
 */
public class CompiledWorkflowGraph {

    private final String versionId;
    private final String workflowKey;
    private final int versionNumber;
    private final WorkflowGraphDto originalGraph;

    private final Map<String, WorkflowNodeDto> nodeMap;
    private final Map<String, List<CompiledEdge>> outgoingEdges;
    private final Map<String, List<CompiledEdge>> incomingEdges;
    private final Map<String, List<WorkflowEdgeDto>> rawOutgoingEdges;
    private final Map<String, List<WorkflowEdgeDto>> rawIncomingEdges;
    private final Map<String, Expression> compiledRuleExpressions;
    private final WorkflowNodeDto startNode;

    public record CompiledEdge(
            WorkflowEdgeDto edgeDto,
            String conditionString,
            Expression compiledCondition
    ) {
        public String getId() { return edgeDto.getId(); }
        public String getSource() { return edgeDto.getSource(); }
        public String getTarget() { return edgeDto.getTarget(); }
        public String getLabel() { return edgeDto.getLabel(); }
        public Map<String, Object> getData() { return edgeDto.getData(); }
    }

    public CompiledWorkflowGraph(String versionId,
                                 String workflowKey,
                                 int versionNumber,
                                 WorkflowGraphDto originalGraph,
                                 Map<String, WorkflowNodeDto> nodeMap,
                                 Map<String, List<CompiledEdge>> outgoingEdges,
                                 Map<String, List<CompiledEdge>> incomingEdges,
                                 Map<String, List<WorkflowEdgeDto>> rawOutgoingEdges,
                                 Map<String, List<WorkflowEdgeDto>> rawIncomingEdges,
                                 Map<String, Expression> compiledRuleExpressions,
                                 WorkflowNodeDto startNode) {
        this.versionId = versionId;
        this.workflowKey = workflowKey;
        this.versionNumber = versionNumber;
        this.originalGraph = originalGraph;
        this.nodeMap = Collections.unmodifiableMap(nodeMap);
        this.outgoingEdges = Collections.unmodifiableMap(outgoingEdges);
        this.incomingEdges = Collections.unmodifiableMap(incomingEdges);
        this.rawOutgoingEdges = Collections.unmodifiableMap(rawOutgoingEdges);
        this.rawIncomingEdges = Collections.unmodifiableMap(rawIncomingEdges);
        this.compiledRuleExpressions = Collections.unmodifiableMap(compiledRuleExpressions);
        this.startNode = startNode;
    }

    public String getVersionId() { return versionId; }
    public String getWorkflowKey() { return workflowKey; }
    public int getVersionNumber() { return versionNumber; }
    public WorkflowGraphDto getOriginalGraph() { return originalGraph; }

    public Map<String, WorkflowNodeDto> getNodeMap() { return nodeMap; }
    public WorkflowNodeDto getNode(String nodeId) { return nodeMap.get(nodeId); }
    public WorkflowNodeDto getStartNode() { return startNode; }

    public List<CompiledEdge> getOutgoingEdges(String nodeId) {
        return outgoingEdges.getOrDefault(nodeId, Collections.emptyList());
    }

    public List<CompiledEdge> getIncomingEdges(String nodeId) {
        return incomingEdges.getOrDefault(nodeId, Collections.emptyList());
    }

    public Map<String, List<WorkflowEdgeDto>> getRawOutgoingEdges() {
        return rawOutgoingEdges;
    }

    public Map<String, List<WorkflowEdgeDto>> getRawIncomingEdges() {
        return rawIncomingEdges;
    }

    public List<WorkflowEdgeDto> getRawOutgoingEdges(String nodeId) {
        return rawOutgoingEdges.getOrDefault(nodeId, Collections.emptyList());
    }

    public List<WorkflowEdgeDto> getRawIncomingEdges(String nodeId) {
        return rawIncomingEdges.getOrDefault(nodeId, Collections.emptyList());
    }

    public Expression getCompiledRuleExpression(String nodeId) {
        return compiledRuleExpressions.get(nodeId);
    }
}
