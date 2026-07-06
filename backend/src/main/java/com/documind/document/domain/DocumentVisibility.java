package com.documind.document.domain;

/** PRIVATE documents are visible only to their uploader; ORG documents are visible to every member of the owning organization. */
public enum DocumentVisibility {
    PRIVATE,
    ORG
}
