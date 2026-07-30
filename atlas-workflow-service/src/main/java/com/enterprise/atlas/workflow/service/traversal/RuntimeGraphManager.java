package com.enterprise.atlas.workflow.service.traversal;

import com.enterprise.atlas.common.dto.WorkflowEdgeDto;
import com.enterprise.atlas.common.dto.WorkflowNodeDto;
import com.enterprise.atlas.workflow.entity.WorkflowInstance;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the mutable runtime-graph state during a traversal.
 *
 * <p>The "runtime graph" is a snapshot of which nodes and edges have been
 * visited in the current execution pass. It is persisted on
 * {@link WorkflowInstance#setRuntimeGraph} and later used to render
 * the execution-replay visualisation in the UI.
 *
 * <p>All logic is copied verbatim from the private methods of
 * {@code GraphTraversalEngine}:
 * <ul>
 *   <li>{@code markNodeActive}</li>
 *   <li>{@code convertNodeToMap}</li>
 *   <li>{@code convertEdgeToMap}</li>
 * </ul>
 */
@Component
public class RuntimeGraphManager {

    /**
     * Adds {@code node} to the active-nodes list if it has not already been
     * recorded, then persists the updated runtime graph on the instance.
     *
     * <p>Uses the {@code TraversalContext#activeNodeIds} set for O(1) duplicate
     * detection when available; falls back to a linear scan otherwise.
     *
     * @param node         the node to mark active
     * @param activeNodes  the mutable list of active-node maps (part of runtimeGraph)
     * @param instance     the workflow instance to update (may be {@code null})
     * @param runtimeGraph the full runtime-graph map
     */
    public void markNodeActive(WorkflowNodeDto node,
                               List<Map<String, Object>> activeNodes,
                               WorkflowInstance instance,
                               Map<String, Object> runtimeGraph) {
        String nodeId = node.getId();
        TraversalContext travCtx = TraversalContextHolder.get();

        if (travCtx != null && travCtx.activeNodeIds != null) {
            if (travCtx.activeNodeIds.add(nodeId)) {
                activeNodes.add(convertNodeToMap(node));
                if (instance != null) {
                    instance.setRuntimeGraph(runtimeGraph);
                }
            }
        } else {
            boolean hasNode = activeNodes.stream().anyMatch(n -> nodeId.equals(n.get("id")));
            if (!hasNode) {
                activeNodes.add(convertNodeToMap(node));
                if (instance != null) {
                    instance.setRuntimeGraph(runtimeGraph);
                }
            }
        }
    }

    /**
     * Adds {@code edge} to the active-edges list if it has not already been
     * recorded.
     *
     * @param edge        the edge to record
     * @param activeEdges the mutable list of active-edge maps (part of runtimeGraph)
     */
    public void markEdgeActive(WorkflowEdgeDto edge, List<Map<String, Object>> activeEdges) {
        boolean hasEdge = activeEdges.stream().anyMatch(e -> edge.getId().equals(e.get("id")));
        if (!hasEdge) {
            activeEdges.add(convertEdgeToMap(edge));
        }
    }

    /**
     * Converts a {@link WorkflowNodeDto} to the plain-map representation stored
     * in the runtime graph (matches what the UI expects).
     */
    public static Map<String, Object> convertNodeToMap(WorkflowNodeDto node) {
        Map<String, Object> map = new HashMap<>();
        map.put("id",       node.getId());
        map.put("type",     node.getType());
        map.put("label",    node.getLabel());
        map.put("data",     node.getData());
        map.put("position", node.getPosition());
        return map;
    }

    /**
     * Converts a {@link WorkflowEdgeDto} to the plain-map representation stored
     * in the runtime graph.
     */
    public static Map<String, Object> convertEdgeToMap(WorkflowEdgeDto edge) {
        Map<String, Object> map = new HashMap<>();
        map.put("id",     edge.getId());
        map.put("source", edge.getSource());
        map.put("target", edge.getTarget());
        map.put("label",  edge.getLabel());
        map.put("data",   edge.getData());
        return map;
    }
}
