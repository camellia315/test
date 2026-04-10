package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    @Update("UPDATE product SET view_count = IFNULL(view_count, 0) + 1 WHERE id = #{productId}")
    int increaseViewCount(@Param("productId") Long productId);

    @Update("UPDATE product SET favorite_count = IFNULL(favorite_count, 0) + 1 WHERE id = #{productId}")
    int increaseFavoriteCount(@Param("productId") Long productId);

    @Update("UPDATE product SET favorite_count = GREATEST(IFNULL(favorite_count, 0) - 1, 0) WHERE id = #{productId}")
    int decreaseFavoriteCount(@Param("productId") Long productId);
}

