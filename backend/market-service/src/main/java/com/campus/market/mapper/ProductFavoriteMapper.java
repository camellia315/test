package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.ProductFavorite;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProductFavoriteMapper extends BaseMapper<ProductFavorite> {

    @Select("SELECT * FROM product_favorite WHERE product_id = #{productId} AND user_id = #{userId} LIMIT 1")
    ProductFavorite selectByProductAndUser(@Param("productId") Long productId, @Param("userId") Long userId);

    @Delete("DELETE FROM product_favorite WHERE product_id = #{productId} AND user_id = #{userId}")
    int deleteByProductAndUser(@Param("productId") Long productId, @Param("userId") Long userId);
}

