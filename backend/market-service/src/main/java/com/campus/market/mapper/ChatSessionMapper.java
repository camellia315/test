package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    @Select("SELECT * FROM chat_session WHERE user_id = #{userId} AND other_user_id = #{otherUserId} LIMIT 1")
    ChatSession selectByUserAndOther(@Param("userId") Long userId, @Param("otherUserId") Long otherUserId);

    @Update("UPDATE chat_session SET unread_count = 0 WHERE user_id = #{userId} AND other_user_id = #{otherUserId}")
    int resetUnreadCount(@Param("userId") Long userId, @Param("otherUserId") Long otherUserId);
}

