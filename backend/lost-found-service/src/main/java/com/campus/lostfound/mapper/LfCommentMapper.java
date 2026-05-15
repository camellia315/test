package com.campus.lostfound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.lostfound.entity.LfComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LfCommentMapper extends BaseMapper<LfComment> {
    @Select("""
            SELECT c.id,
                   c.lost_found_id AS lostFoundId,
                   c.user_id AS userId,
                   c.content,
                   c.created_at AS createdAt,
                   u.user_id AS commenterUserId,
                   u.user_no AS commenterUserNo,
                   u.username AS commenterUsername,
                   u.avatar_url AS commenterAvatarUrl
            FROM lf_comment c
            LEFT JOIN `user` u ON u.id = c.user_id
            WHERE c.lost_found_id = #{lostFoundId}
            ORDER BY c.created_at ASC, c.id ASC
            """)
    List<LfComment> selectWithUserInfo(@Param("lostFoundId") Long lostFoundId);
}
