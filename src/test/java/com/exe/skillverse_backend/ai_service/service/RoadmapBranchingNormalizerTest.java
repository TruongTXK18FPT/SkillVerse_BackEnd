package com.exe.skillverse_backend.ai_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoadmapBranchingNormalizerTest {

    @Test
    void normalize_keepsBasicRoadmapAsStraightMainSpine() {
        RoadmapResponse.RoadmapNode first = main("m1", "Foundation");
        RoadmapResponse.RoadmapNode second = main("m2", "Core");
        RoadmapResponse.RoadmapNode third = main("m3", "Project");

        RoadmapBranchingNormalizer.Result result = RoadmapBranchingNormalizer.normalize(List.of(first, second, third));

        assertEquals(3, result.mainNodes());
        assertEquals(0, result.sideNodes());
        assertEquals(List.of("m2"), result.nodes().get(0).getChildren());
        assertEquals(List.of("m1"), result.nodes().get(1).getPrerequisites());
        assertEquals(List.of("m2"), result.nodes().get(2).getPrerequisites());
        assertEquals(1, result.nodes().get(0).getMainPathIndex());
        assertEquals(2, result.nodes().get(1).getMainPathIndex());
        assertEquals(3, result.nodes().get(2).getMainPathIndex());
    }

    @Test
    void normalize_attachesSideNodesToMainParentWithoutBlockingNextMain() {
        RoadmapResponse.RoadmapNode first = main("m1", "Foundation");
        RoadmapResponse.RoadmapNode side = side("s1", "Extra practice");
        side.setParentId("m1");
        RoadmapResponse.RoadmapNode second = main("m2", "Core");

        RoadmapBranchingNormalizer.Result result = RoadmapBranchingNormalizer.normalize(List.of(first, side, second));

        RoadmapResponse.RoadmapNode normalizedFirst = result.nodes().get(0);
        RoadmapResponse.RoadmapNode normalizedSecond = result.nodes().get(1);
        RoadmapResponse.RoadmapNode normalizedSide = result.nodes().get(2);

        assertEquals(List.of("m2", "s1"), normalizedFirst.getChildren());
        assertEquals(List.of("m1"), normalizedSecond.getPrerequisites());
        assertEquals("m1", normalizedSide.getParentId());
        assertEquals(List.of("m1"), normalizedSide.getPrerequisites());
        assertEquals(RoadmapResponse.RoadmapNode.NodeType.SIDE, normalizedSide.getType());
        assertEquals(1, normalizedSide.getMainPathIndex());
    }

    @Test
    void normalize_removesSidePrerequisiteFromMainNode() {
        RoadmapResponse.RoadmapNode first = main("m1", "Foundation");
        RoadmapResponse.RoadmapNode side = side("s1", "Optional deep dive");
        side.setParentId("m1");
        RoadmapResponse.RoadmapNode second = main("m2", "Core");
        second.setPrerequisites(List.of("s1"));

        RoadmapBranchingNormalizer.Result result = RoadmapBranchingNormalizer.normalize(List.of(first, side, second));

        RoadmapResponse.RoadmapNode normalizedSecond = result.nodes().stream()
                .filter(node -> "m2".equals(node.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(List.of("m1"), normalizedSecond.getPrerequisites());
        assertTrue(result.nodes().stream()
                .filter(node -> node.getType() == RoadmapResponse.RoadmapNode.NodeType.MAIN)
                .noneMatch(node -> node.getPrerequisites() != null && node.getPrerequisites().contains("s1")));
    }

    @Test
    void normalize_capsGeneratedRoadmapToEightMainAndSixSideNodes() {
        List<RoadmapResponse.RoadmapNode> nodes = List.of(
                main("m1", "Main 1"),
                main("m2", "Main 2"),
                main("m3", "Main 3"),
                main("m4", "Main 4"),
                main("m5", "Main 5"),
                main("m6", "Main 6"),
                main("m7", "Main 7"),
                main("m8", "Main 8"),
                main("m9", "Overflow main 9"),
                side("s1", "Side 1"),
                side("s2", "Side 2"),
                side("s3", "Side 3"),
                side("s4", "Side 4"),
                side("s5", "Side 5"),
                side("s6", "Side 6"),
                side("s7", "Dropped side 7"));

        RoadmapBranchingNormalizer.Result result = RoadmapBranchingNormalizer.normalize(nodes);

        assertEquals(8, result.mainNodes());
        assertEquals(6, result.sideNodes());
        assertEquals(14, result.nodes().size());
        assertEquals(8, result.nodes().stream()
                .filter(node -> node.getType() == RoadmapResponse.RoadmapNode.NodeType.MAIN)
                .count());
        assertEquals(6, result.nodes().stream()
                .filter(node -> node.getType() == RoadmapResponse.RoadmapNode.NodeType.SIDE)
                .count());
    }

    @Test
    void normalize_promotesSideNodesToReachEightMainNodes() {
        List<RoadmapResponse.RoadmapNode> nodes = List.of(
                main("m1", "Main 1"),
                main("m2", "Main 2"),
                main("m3", "Main 3"),
                main("m4", "Main 4"),
                main("m5", "Main 5"),
                main("m6", "Main 6"),
                main("m7", "Main 7"),
                side("s1", "Promoted side"),
                side("s2", "Remaining side 2"),
                side("s3", "Remaining side 3"),
                side("s4", "Remaining side 4"));

        RoadmapBranchingNormalizer.Result result = RoadmapBranchingNormalizer.normalize(nodes);

        assertEquals(8, result.mainNodes());
        assertEquals(3, result.sideNodes());
        assertEquals(RoadmapResponse.RoadmapNode.NodeType.MAIN,
                result.nodes().stream().filter(node -> "s1".equals(node.getId())).findFirst().orElseThrow().getType());
    }

    private RoadmapResponse.RoadmapNode main(String id, String title) {
        return RoadmapResponse.RoadmapNode.builder()
                .id(id)
                .title(title)
                .type(RoadmapResponse.RoadmapNode.NodeType.MAIN)
                .isCore(true)
                .prerequisites(List.of())
                .children(List.of())
                .build();
    }

    private RoadmapResponse.RoadmapNode side(String id, String title) {
        return RoadmapResponse.RoadmapNode.builder()
                .id(id)
                .title(title)
                .type(RoadmapResponse.RoadmapNode.NodeType.SIDE)
                .isCore(false)
                .prerequisites(List.of())
                .children(List.of())
                .build();
    }
}
