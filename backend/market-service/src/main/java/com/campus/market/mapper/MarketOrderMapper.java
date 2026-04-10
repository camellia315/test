package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.MarketOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MarketOrderMapper extends BaseMapper<MarketOrder> {

    @Select("SELECT COUNT(1) FROM market_order WHERE product_id = #{productId} AND status IN (0, 1)")
    int countActiveOrdersByProduct(@Param("productId") Long productId);
}

