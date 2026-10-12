package com.vegalife.repository.post;

import com.vegalife.model.post.Post;
import java.util.UUID;

/** One (media id, post) pair, so a page of videos costs a single associated-posts query. */
public record MediaPostRow(UUID mediaId, Post post) {}
