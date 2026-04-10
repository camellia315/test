package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.ChatMessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {

    @Update("UPDATE chat_message SET is_read = 1 " +
            "WHERE to_user_id = #{userId} AND from_user_id = #{otherUserId} AND is_read = 0")
    int markAsRead(@Param("userId") Long userId, @Param("otherUserId") Long otherUserId);
}

