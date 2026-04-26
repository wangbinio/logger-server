package com.szzh.replayserver.service;

import com.szzh.replayserver.model.query.ReplayFrame;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 回放帧归并服务。
 */
@Service
public class ReplayFrameMergeService {

    private static final Comparator<ReplayFrame> FRAME_COMPARATOR =
            Comparator.comparingLong(ReplayFrame::getSimTime)
                    .thenComparingInt(ReplayFrame::getMessageType)
                    .thenComparingInt(ReplayFrame::getMessageCode)
                    .thenComparingInt(ReplayFrame::getSenderId)
                    .thenComparing(ReplayFrame::getTableName);

    /**
     * 归并多表分页查询结果。
     *
     * @param framePages 多表分页帧。
     * @return 排序后的回放帧。
     */
    public List<ReplayFrame> merge(Collection<List<ReplayFrame>> framePages) {
        if (framePages == null || framePages.isEmpty()) {
            return Collections.emptyList();
        }
        List<ReplayFrame> merged = new ArrayList<ReplayFrame>();
        for (List<ReplayFrame> framePage : framePages) {
            if (framePage != null) {
                merged.addAll(framePage);
            }
        }
        merged.sort(FRAME_COMPARATOR);
        return merged;
    }
}
