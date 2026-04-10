package com.campus.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.activity.entity.Activity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface ActivityMapper extends BaseMapper<Activity> {

    @Update("UPDATE activity " +
            "SET current_participants = current_participants + 1 " +
            "WHERE id = #{activityId} " +
            "AND status = 1 " +
            "AND (max_participants = 0 OR current_participants < max_participants)")
    int increaseParticipantsIfAvailable(@Param("activityId") Long activityId);

    @Update("UPDATE activity " +
            "SET current_participants = CASE WHEN current_participants > 0 THEN current_participants - 1 ELSE 0 END " +
            "WHERE id = #{activityId}")
    int decreaseParticipants(@Param("activityId") Long activityId);

    @Update("UPDATE activity SET status = 2 WHERE status = 1 AND end_time IS NOT NULL AND end_time <= #{now}")
    int syncEndedActivities(@Param("now") LocalDateTime now);
}
