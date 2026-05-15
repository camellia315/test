package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    @Update("UPDATE product SET view_count = IFNULL(view_count, 0) + 1 WHERE id = #{productId}")
    int increaseViewCount(@Param("productId") Long productId);

    @Update("UPDATE product SET favorite_count = IFNULL(favorite_count, 0) + 1 WHERE id = #{productId}")
    int increaseFavoriteCount(@Param("productId") Long productId);

    @Update("UPDATE product SET favorite_count = GREATEST(IFNULL(favorite_count, 0) - 1, 0) WHERE id = #{productId}")
    int decreaseFavoriteCount(@Param("productId") Long productId);

    @Select("SELECT IFNULL(total_quantity, 1) FROM product WHERE id = #{productId}")
    Integer selectTotalQuantity(@Param("productId") Long productId);

    @Select("SELECT IFNULL(sold_quantity, 0) FROM product WHERE id = #{productId}")
    Integer selectSoldQuantity(@Param("productId") Long productId);

    @Update("UPDATE product SET sold_quantity = LEAST(IFNULL(sold_quantity, 0) + 1, GREATEST(IFNULL(total_quantity, 1), 1)) WHERE id = #{productId}")
    int increaseSoldQuantity(@Param("productId") Long productId);

    @Update("UPDATE product SET sold_quantity = GREATEST(IFNULL(sold_quantity, 0) - 1, 0) WHERE id = #{productId}")
    int decreaseSoldQuantity(@Param("productId") Long productId);
}
