package com.exe.skillverse_backend.ai_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoadmapGraphCanonicalizerTest {

    @Test
    void canonicalize_keepsPrerequisitesAsDependencyOnlyHints() {
        RoadmapResponse.RoadmapNode root = RoadmapResponse.RoadmapNode.builder()
                .id("root")
                .title("Root")
                .type(RoadmapResponse.RoadmapNode.NodeType.MAIN)
                .build();
        RoadmapResponse.RoadmapNode dependencyOnly = RoadmapResponse.RoadmapNode.builder()
                .id("branch")
                .title("Branch")
                .type(RoadmapResponse.RoadmapNode.NodeType.SIDE)
                .prerequisites(List.of("root"))
                .build();

        RoadmapGraphCanonicalizer.Result result = RoadmapGraphCanonicalizer.canonicalize(List.of(root, dependencyOnly));

        assertEquals(2, result.rootCount());
        assertEquals(0, result.childDerivedParents());
        assertEquals(null, result.nodes().get(1).getParentId());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("preserving as root-like node")));
    }

    @Test
    void canonicalize_derivesParentFromChildrenWhenParentIdMissing() {
        RoadmapResponse.RoadmapNode parent = RoadmapResponse.RoadmapNode.builder()
                .id("main-1")
                .title("Main")
                .type(RoadmapResponse.RoadmapNode.NodeType.MAIN)
                .children(List.of("side-1"))
                .build();
        RoadmapResponse.RoadmapNode child = RoadmapResponse.RoadmapNode.builder()
                .id("side-1")
                .title("Side")
                .type(RoadmapResponse.RoadmapNode.NodeType.SIDE)
                .build();

        RoadmapGraphCanonicalizer.Result result = RoadmapGraphCanonicalizer.canonicalize(List.of(parent, child));

        assertEquals(1, result.rootCount());
        assertEquals(1, result.branchCount());
        assertEquals(1, result.childDerivedParents());
        assertEquals("main-1", result.nodes().get(1).getParentId());
        assertEquals(List.of("side-1"), result.nodes().get(0).getChildren());
    }

    @Test
    void canonicalize_rebuildsChildrenFromExplicitParentIds() {
        RoadmapResponse.RoadmapNode parent = RoadmapResponse.RoadmapNode.builder()
                .id("main-1")
                .title("Main")
                .type(RoadmapResponse.RoadmapNode.NodeType.MAIN)
                .children(List.of())
                .build();
        RoadmapResponse.RoadmapNode child = RoadmapResponse.RoadmapNode.builder()
                .id("main-2")
                .title("Main 2")
                .type(RoadmapResponse.RoadmapNode.NodeType.MAIN)
                .parentId("main-1")
                .build();

        RoadmapGraphCanonicalizer.Result result = RoadmapGraphCanonicalizer.canonicalize(List.of(parent, child));

        assertEquals(1, result.rootCount());
        assertEquals(1, result.branchCount());
        assertEquals(List.of("main-2"), result.nodes().get(0).getChildren());
    }
}
