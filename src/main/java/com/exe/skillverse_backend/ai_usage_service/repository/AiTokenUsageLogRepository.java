package com.exe.skillverse_backend.ai_usage_service.repository;

import com.exe.skillverse_backend.ai_usage_service.entity.AiTokenUsageLog;
import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiFlowType;
import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiProviderType;
import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiUsageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AiTokenUsageLogRepository extends JpaRepository<AiTokenUsageLog, Long> {

    @Query("SELECT COALESCE(SUM(l.totalTokens), 0) FROM AiTokenUsageLog l WHERE l.createdAt BETWEEN :from AND :to")
    Long sumTotalTokensBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(l.totalTokens), 0) FROM AiTokenUsageLog l WHERE " +
           "(:flowType IS NULL OR l.flowType = :flowType) AND " +
           "(:providerType IS NULL OR l.providerType = :providerType) AND " +
           "(:status IS NULL OR l.status = :status) AND " +
           "l.createdAt BETWEEN :from AND :to")
    Long sumTotalTokensFiltered(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                 @Param("flowType") AiFlowType flowType,
                                 @Param("providerType") AiProviderType providerType,
                                 @Param("status") AiUsageStatus status);

    @Query("SELECT COALESCE(SUM(l.promptTokens), 0) FROM AiTokenUsageLog l WHERE " +
           "(:flowType IS NULL OR l.flowType = :flowType) AND " +
           "(:providerType IS NULL OR l.providerType = :providerType) AND " +
           "(:status IS NULL OR l.status = :status) AND " +
           "l.createdAt BETWEEN :from AND :to")
    Long sumPromptTokensFiltered(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                 @Param("flowType") AiFlowType flowType,
                                 @Param("providerType") AiProviderType providerType,
                                 @Param("status") AiUsageStatus status);

    @Query("SELECT COALESCE(SUM(l.completionTokens), 0) FROM AiTokenUsageLog l WHERE " +
           "(:flowType IS NULL OR l.flowType = :flowType) AND " +
           "(:providerType IS NULL OR l.providerType = :providerType) AND " +
           "(:status IS NULL OR l.status = :status) AND " +
           "l.createdAt BETWEEN :from AND :to")
    Long sumCompletionTokensFiltered(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                     @Param("flowType") AiFlowType flowType,
                                     @Param("providerType") AiProviderType providerType,
                                     @Param("status") AiUsageStatus status);

    @Query("SELECT COALESCE(SUM(l.totalTokens), 0) FROM AiTokenUsageLog l WHERE l.estimated = true AND " +
           "(:flowType IS NULL OR l.flowType = :flowType) AND " +
           "(:providerType IS NULL OR l.providerType = :providerType) AND " +
           "(:status IS NULL OR l.status = :status) AND " +
           "l.createdAt BETWEEN :from AND :to")
    Long sumEstimatedTokensFiltered(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                   @Param("flowType") AiFlowType flowType,
                                   @Param("providerType") AiProviderType providerType,
                                   @Param("status") AiUsageStatus status);

    @Query("SELECT COUNT(l) FROM AiTokenUsageLog l WHERE " +
           "(:flowType IS NULL OR l.flowType = :flowType) AND " +
           "(:providerType IS NULL OR l.providerType = :providerType) AND " +
           "(:status IS NULL OR l.status = :status) AND " +
           "l.createdAt BETWEEN :from AND :to")
    Long countFiltered(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                      @Param("flowType") AiFlowType flowType,
                      @Param("providerType") AiProviderType providerType,
                      @Param("status") AiUsageStatus status);

    @Query("SELECT COUNT(l) FROM AiTokenUsageLog l WHERE l.status = 'SUCCESS' AND " +
           "(:flowType IS NULL OR l.flowType = :flowType) AND " +
           "(:providerType IS NULL OR l.providerType = :providerType) AND " +
           "l.createdAt BETWEEN :from AND :to")
    Long countSuccessFiltered(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                             @Param("flowType") AiFlowType flowType,
                             @Param("providerType") AiProviderType providerType);

    @Query("SELECT COUNT(l) FROM AiTokenUsageLog l WHERE l.status = 'FAILED' AND " +
           "(:flowType IS NULL OR l.flowType = :flowType) AND " +
           "(:providerType IS NULL OR l.providerType = :providerType) AND " +
           "l.createdAt BETWEEN :from AND :to")
    Long countFailedFiltered(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                            @Param("flowType") AiFlowType flowType,
                            @Param("providerType") AiProviderType providerType);

    @Query("SELECT COALESCE(AVG(l.latencyMs), 0) FROM AiTokenUsageLog l WHERE l.latencyMs IS NOT NULL AND " +
           "(:flowType IS NULL OR l.flowType = :flowType) AND " +
           "(:providerType IS NULL OR l.providerType = :providerType) AND " +
           "(:status IS NULL OR l.status = :status) AND " +
           "l.createdAt BETWEEN :from AND :to")
    Double averageLatencyFiltered(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                 @Param("flowType") AiFlowType flowType,
                                 @Param("providerType") AiProviderType providerType,
                                 @Param("status") AiUsageStatus status);

    @Query("SELECT l.flowType, SUM(l.totalTokens), COUNT(l) FROM AiTokenUsageLog l WHERE l.createdAt BETWEEN :from AND :to GROUP BY l.flowType ORDER BY SUM(l.totalTokens) DESC")
    List<Object[]> sumTokensByFlowTypeBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT l.providerType, SUM(l.totalTokens), COUNT(l) FROM AiTokenUsageLog l WHERE l.createdAt BETWEEN :from AND :to GROUP BY l.providerType ORDER BY SUM(l.totalTokens) DESC")
    List<Object[]> sumTokensByProviderTypeBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT l.modelName, SUM(l.totalTokens), COUNT(l) FROM AiTokenUsageLog l WHERE l.modelName IS NOT NULL AND l.createdAt BETWEEN :from AND :to GROUP BY l.modelName ORDER BY SUM(l.totalTokens) DESC")
    List<Object[]> sumTokensByModelBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT l.status, SUM(l.totalTokens), COUNT(l) FROM AiTokenUsageLog l WHERE l.createdAt BETWEEN :from AND :to GROUP BY l.status")
    List<Object[]> sumTokensByStatusBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    Page<AiTokenUsageLog> findByFlowTypeAndCreatedAtBetween(
        AiFlowType flowType, LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<AiTokenUsageLog> findByProviderTypeAndCreatedAtBetween(
        AiProviderType providerType, LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<AiTokenUsageLog> findByStatusAndCreatedAtBetween(
        AiUsageStatus status, LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<AiTokenUsageLog> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);

    @Query("SELECT l FROM AiTokenUsageLog l WHERE " +
           "(:flowType IS NULL OR l.flowType = :flowType) AND " +
           "(:providerType IS NULL OR l.providerType = :providerType) AND " +
           "(:status IS NULL OR l.status = :status) AND " +
           "(:modelName IS NULL OR l.modelName = :modelName) AND " +
           "l.createdAt BETWEEN :from AND :to")
    Page<AiTokenUsageLog> findByFilters(@Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to,
                                       @Param("flowType") AiFlowType flowType,
                                       @Param("providerType") AiProviderType providerType,
                                       @Param("status") AiUsageStatus status,
                                       @Param("modelName") String modelName,
                                       Pageable pageable);

    @Query("SELECT l.flowType, FUNCTION('DATE_TRUNC', 'hour', l.createdAt), SUM(l.promptTokens), SUM(l.completionTokens), SUM(l.totalTokens), COUNT(l), SUM(CASE WHEN l.status = 'FAILED' THEN 1 ELSE 0 END) " +
           "FROM AiTokenUsageLog l WHERE l.createdAt BETWEEN :from AND :to " +
           "GROUP BY l.flowType, FUNCTION('DATE_TRUNC', 'hour', l.createdAt) " +
           "ORDER BY FUNCTION('DATE_TRUNC', 'hour', l.createdAt)")
    List<Object[]> hourlyTimeSeriesBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT l.flowType, FUNCTION('DATE_TRUNC', 'day', l.createdAt), SUM(l.promptTokens), SUM(l.completionTokens), SUM(l.totalTokens), COUNT(l), SUM(CASE WHEN l.status = 'FAILED' THEN 1 ELSE 0 END) " +
           "FROM AiTokenUsageLog l WHERE l.createdAt BETWEEN :from AND :to " +
           "GROUP BY l.flowType, FUNCTION('DATE_TRUNC', 'day', l.createdAt) " +
           "ORDER BY FUNCTION('DATE_TRUNC', 'day', l.createdAt)")
    List<Object[]> dailyTimeSeriesBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // Filtered timeseries queries
    @Query("SELECT l.flowType, FUNCTION('DATE_TRUNC', 'hour', l.createdAt), SUM(l.promptTokens), SUM(l.completionTokens), SUM(l.totalTokens), COUNT(l), SUM(CASE WHEN l.status = 'FAILED' THEN 1 ELSE 0 END) " +
           "FROM AiTokenUsageLog l WHERE " +
           "(:flowType IS NULL OR l.flowType = :flowType) AND " +
           "(:providerType IS NULL OR l.providerType = :providerType) AND " +
           "(:status IS NULL OR l.status = :status) AND " +
           "l.createdAt BETWEEN :from AND :to " +
           "GROUP BY l.flowType, FUNCTION('DATE_TRUNC', 'hour', l.createdAt) " +
           "ORDER BY FUNCTION('DATE_TRUNC', 'hour', l.createdAt)")
    List<Object[]> hourlyTimeSeriesFiltered(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                           @Param("flowType") AiFlowType flowType,
                                           @Param("providerType") AiProviderType providerType,
                                           @Param("status") AiUsageStatus status);

    @Query("SELECT l.flowType, FUNCTION('DATE_TRUNC', 'day', l.createdAt), SUM(l.promptTokens), SUM(l.completionTokens), SUM(l.totalTokens), COUNT(l), SUM(CASE WHEN l.status = 'FAILED' THEN 1 ELSE 0 END) " +
           "FROM AiTokenUsageLog l WHERE " +
           "(:flowType IS NULL OR l.flowType = :flowType) AND " +
           "(:providerType IS NULL OR l.providerType = :providerType) AND " +
           "(:status IS NULL OR l.status = :status) AND " +
           "l.createdAt BETWEEN :from AND :to " +
           "GROUP BY l.flowType, FUNCTION('DATE_TRUNC', 'day', l.createdAt) " +
           "ORDER BY FUNCTION('DATE_TRUNC', 'day', l.createdAt)")
    List<Object[]> dailyTimeSeriesFiltered(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                          @Param("flowType") AiFlowType flowType,
                                          @Param("providerType") AiProviderType providerType,
                                          @Param("status") AiUsageStatus status);

    // Filtered breakdown queries
    @Query("SELECT l.flowType, SUM(l.totalTokens), COUNT(l) FROM AiTokenUsageLog l WHERE " +
           "(:flowType IS NULL OR l.flowType = :flowType) AND " +
           "(:providerType IS NULL OR l.providerType = :providerType) AND " +
           "(:status IS NULL OR l.status = :status) AND " +
           "l.createdAt BETWEEN :from AND :to GROUP BY l.flowType ORDER BY SUM(l.totalTokens) DESC")
    List<Object[]> sumTokensByFlowTypeFiltered(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                              @Param("flowType") AiFlowType flowType,
                                              @Param("providerType") AiProviderType providerType,
                                              @Param("status") AiUsageStatus status);

    @Query("SELECT l.providerType, SUM(l.totalTokens), COUNT(l) FROM AiTokenUsageLog l WHERE " +
           "(:flowType IS NULL OR l.flowType = :flowType) AND " +
           "(:providerType IS NULL OR l.providerType = :providerType) AND " +
           "(:status IS NULL OR l.status = :status) AND " +
           "l.createdAt BETWEEN :from AND :to GROUP BY l.providerType ORDER BY SUM(l.totalTokens) DESC")
    List<Object[]> sumTokensByProviderTypeFiltered(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                                  @Param("flowType") AiFlowType flowType,
                                                  @Param("providerType") AiProviderType providerType,
                                                  @Param("status") AiUsageStatus status);

    @Query("SELECT l.modelName, SUM(l.totalTokens), COUNT(l) FROM AiTokenUsageLog l WHERE l.modelName IS NOT NULL AND " +
           "(:flowType IS NULL OR l.flowType = :flowType) AND " +
           "(:providerType IS NULL OR l.providerType = :providerType) AND " +
           "(:status IS NULL OR l.status = :status) AND " +
           "l.createdAt BETWEEN :from AND :to GROUP BY l.modelName ORDER BY SUM(l.totalTokens) DESC")
    List<Object[]> sumTokensByModelFiltered(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                           @Param("flowType") AiFlowType flowType,
                                           @Param("providerType") AiProviderType providerType,
                                           @Param("status") AiUsageStatus status);

    @Query("SELECT l.status, SUM(l.totalTokens), COUNT(l) FROM AiTokenUsageLog l WHERE " +
           "(:flowType IS NULL OR l.flowType = :flowType) AND " +
           "(:providerType IS NULL OR l.providerType = :providerType) AND " +
           "(:status IS NULL OR l.status = :status) AND " +
           "l.createdAt BETWEEN :from AND :to GROUP BY l.status")
    List<Object[]> sumTokensByStatusFiltered(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                            @Param("flowType") AiFlowType flowType,
                                            @Param("providerType") AiProviderType providerType,
                                            @Param("status") AiUsageStatus status);
}
