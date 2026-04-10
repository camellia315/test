package com.campus.lostfound.service;

import com.campus.lostfound.dto.LfCommentCreateRequest;
import com.campus.lostfound.dto.LostFoundCreateRequest;
import com.campus.lostfound.entity.LfComment;
import com.campus.lostfound.entity.LostFoundItem;

import java.util.List;
import java.util.Map;

public interface LostFoundService {
    Map<String, Object> pageItems(int page, int size, String keyword, String status, String itemType);

    LostFoundItem getById(Long id);

    LostFoundItem createItem(LostFoundCreateRequest request);

    LostFoundItem updateStatus(Long id, String status);

    void deleteItem(Long id, Long operatorUserId);

    List<LfComment> listComments(Long lostFoundId);

    LfComment createComment(Long lostFoundId, LfCommentCreateRequest request);
}
