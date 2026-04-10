package com.campus.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.activity.entity.ActivityApply;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ActivityApplyMapper extends BaseMapper<ActivityApply> {

    @Select("SELECT * FROM activity_apply WHERE activity_id = #{activityId} AND user_id = #{userId} LIMIT 1")
    ActivityApply selectByActivityAndUser(@Param("activityId") Long activityId, @Param("userId") Long userId);
}
