package com.campus.lostfound.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.exception.BusinessException;
import com.campus.lostfound.dto.LfCommentCreateRequest;
import com.campus.lostfound.dto.LostFoundCreateRequest;
import com.campus.lostfound.entity.LfComment;
import com.campus.lostfound.entity.LostFoundItem;
import com.campus.lostfound.mapper.LfCommentMapper;
import com.campus.lostfound.mapper.LostFoundItemMapper;
import com.campus.lostfound.service.FileStorageService;
import com.campus.lostfound.service.LostFoundService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LostFoundServiceImpl implements LostFoundService {

    private static final Set<String> VALID_ITEM_TYPES = Set.of("LOST", "FOUND");
    private static final Set<String> VALID_STATUS = Set.of("SEARCHING", "FOUND", "RETURNED");

    private final LostFoundItemMapper itemMapper;
    private final LfCommentMapper commentMapper;
    private final FileStorageService fileStorageService;

    public LostFoundServiceImpl(LostFoundItemMapper itemMapper,
                                LfCommentMapper commentMapper,
                                FileStorageService fileStorageService) {
        this.itemMapper = itemMapper;
        this.commentMapper = commentMapper;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public Map<String, Object> pageItems(int page, int size, String keyword, String status, String itemType) {
        Page<LostFoundItem> pageReq = new Page<>(page, size);
        LambdaQueryWrapper<LostFoundItem> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(LostFoundItem::getTitle, keyword)
                    .or()
                    .like(LostFoundItem::getDescription, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(LostFoundItem::getStatus, status.trim().toUpperCase());
        }
        if (StringUtils.hasText(itemType)) {
            wrapper.eq(LostFoundItem::getItemType, itemType.trim().toUpperCase());
        }
        wrapper.orderByDesc(LostFoundItem::getCreatedAt);

        Page<LostFoundItem> result = itemMapper.selectPage(pageReq, wrapper);
        Map<String, Object> response = new HashMap<>();
        response.put("records", result.getRecords());
        response.put("total", result.getTotal());
        response.put("pages", result.getPages());
        response.put("current", result.getCurrent());
        response.put("size", result.getSize());
        return response;
    }

    @Override
    public LostFoundItem getById(Long id) {
        LostFoundItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(404, "Lost-found item not found");
        }
        return item;
    }

    @Override
    public LostFoundItem createItem(LostFoundCreateRequest request) {
        if (request.getUserId() == null) {
            throw new BusinessException(400, "userId is required");
        }
        if (!StringUtils.hasText(request.getTitle())) {
            throw new BusinessException(400, "title is required");
        }
        if (!StringUtils.hasText(request.getItemType())) {
            throw new BusinessException(400, "itemType is required");
        }

        String itemType = request.getItemType().trim().toUpperCase();
        if (!VALID_ITEM_TYPES.contains(itemType)) {
            throw new BusinessException(400, "itemType must be LOST or FOUND");
        }

        LostFoundItem item = new LostFoundItem();
        item.setUserId(request.getUserId());
        item.setCategoryId(request.getCategoryId());
        item.setTitle(request.getTitle().trim());
        item.setDescription(request.getDescription());
        item.setImageUrl(request.getImageUrl());
        item.setLocationText(request.getLocationText());
        item.setItemType(itemType);
        item.setStatus("SEARCHING");
        item.setCreatedAt(LocalDateTime.now());

        itemMapper.insert(item);
        return item;
    }

    @Override
    public LostFoundItem updateStatus(Long id, String status) {
        if (!StringUtils.hasText(status)) {
            throw new BusinessException(400, "status is required");
        }
        String normalizedStatus = status.trim().toUpperCase();
        if (!VALID_STATUS.contains(normalizedStatus)) {
            throw new BusinessException(400, "status must be one of " + Arrays.toString(VALID_STATUS.toArray()));
        }

        LostFoundItem item = getById(id);
        item.setStatus(normalizedStatus);
        itemMapper.updateById(item);
        return item;
    }

    @Override
    public void deleteItem(Long id, Long operatorUserId) {
        if (operatorUserId == null) {
            throw new BusinessException(400, "operatorUserId is required");
        }

        LostFoundItem item = getById(id);
        if (!operatorUserId.equals(item.getUserId())) {
            throw new BusinessException(403, "Only publisher can delete this item");
        }

        LambdaQueryWrapper<LfComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LfComment::getLostFoundId, id);
        commentMapper.delete(wrapper);
        itemMapper.deleteById(id);

        if (StringUtils.hasText(item.getImageUrl())) {
            try {
                fileStorageService.deleteImage(item.getImageUrl());
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public List<LfComment> listComments(Long lostFoundId) {
        getById(lostFoundId);
        LambdaQueryWrapper<LfComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LfComment::getLostFoundId, lostFoundId);
        wrapper.orderByAsc(LfComment::getCreatedAt);
        return commentMapper.selectList(wrapper);
    }

    @Override
    public LfComment createComment(Long lostFoundId, LfCommentCreateRequest request) {
        getById(lostFoundId);
        if (request.getUserId() == null) {
            throw new BusinessException(400, "userId is required");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new BusinessException(400, "content is required");
        }

        LfComment comment = new LfComment();
        comment.setLostFoundId(lostFoundId);
        comment.setUserId(request.getUserId());
        comment.setContent(request.getContent().trim());
        comment.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(comment);
        return comment;
    }
}
