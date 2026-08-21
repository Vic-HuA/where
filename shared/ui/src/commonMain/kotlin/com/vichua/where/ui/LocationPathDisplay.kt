package com.vichua.where.ui

/**
 * 把存储路径里的间隔号换成层级箭头，避免被看成并列标签。
 *
 * 索引和分享原文仍使用原来的间隔号，这里只改界面展示。
 */
fun visibleLocationPath(path: String): String =
    path.replace(STORED_LOCATION_PATH_SEPARATOR, VISIBLE_LOCATION_PATH_SEPARATOR)

private const val STORED_LOCATION_PATH_SEPARATOR = " · "
private const val VISIBLE_LOCATION_PATH_SEPARATOR = " > "
