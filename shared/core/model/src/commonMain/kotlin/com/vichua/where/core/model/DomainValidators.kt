package com.vichua.where.core.model

/**
 * 对需要跨多条记录判断的数据不变量进行集中校验。
 *
 * 单个实体构造函数只校验自身字段；位置树环路、同级重名、封面唯一性等集合约束必须在
 * 仓储写入前调用这里的校验函数，并与正式写入保持在同一业务事务中。
 */
object DomainValidators {
    /**
     * 校验一个家庭当前未删除的位置树。
     *
     * 校验内容包括唯一根节点、父节点存在性、家庭隔离、同级同类型重名和位置树环路。
     *
     * @param householdId 需要校验的家庭。
     * @param locations 可包含软删除记录的家庭位置集合。
     */
    fun validateLocationTree(
        householdId: HouseholdId,
        locations: Collection<LocationNode>,
    ) {
        val activeLocations = locations.filter { it.deletedAt == null }
        require(activeLocations.all { it.householdId == householdId }) {
            "Location tree contains nodes from another household."
        }

        val rootLocations = activeLocations.filter { it.type == LocationType.HOME }
        require(rootLocations.size == 1) {
            "Location tree must contain exactly one active home root."
        }

        val locationsById = activeLocations.associateBy(LocationNode::id)
        require(locationsById.size == activeLocations.size) {
            "Location tree contains duplicate active IDs."
        }

        activeLocations
            .filterNot(LocationNode::isHouseholdRoot)
            .forEach { location ->
                require(locationsById.containsKey(location.parentId)) {
                    "Active non-home location must reference an active parent."
                }
            }

        val duplicateSiblingKeys = activeLocations
            .groupBy { location ->
                Triple(location.parentId, location.type, location.normalizedName)
            }
            .filterValues { siblings -> siblings.size > 1 }
        require(duplicateSiblingKeys.isEmpty()) {
            "Location tree contains duplicate sibling names and types."
        }

        activeLocations.forEach { location ->
            validateNoLocationCycle(location, locationsById)
        }
    }

    /**
     * 校验物品当前位置与物品状态是否一致。
     *
     * @param item 需要校验的物品。
     * @param locations 可包含软删除记录的位置集合。
     */
    fun validateItemLocation(
        item: Item,
        locations: Collection<LocationNode>,
    ) {
        val currentLocation = locations.singleOrNull { location ->
            location.id == item.currentLocationId && location.deletedAt == null
        }
        require(currentLocation != null) {
            "Item must reference exactly one active current location."
        }
        require(currentLocation.householdId == item.householdId) {
            "Item and current location must belong to the same household."
        }
        require(
            item.status != ItemStatus.LOCATION_UNCONFIRMED ||
                currentLocation.type == LocationType.HOME,
        ) {
            "Location-unconfirmed item must reference the household root."
        }
        require(
            item.status == ItemStatus.LOCATION_UNCONFIRMED ||
                currentLocation.type != LocationType.HOME,
        ) {
            "Confirmed item must not use the household root as its normal location."
        }
    }

    /**
     * 校验物品未删除别名的归属关系和标准化值唯一性。
     *
     * @param itemId 别名所属物品。
     * @param aliases 可包含软删除记录的别名集合。
     */
    fun validateItemAliases(
        itemId: ItemId,
        aliases: Collection<ItemAlias>,
    ) {
        require(aliases.all { it.itemId == itemId }) {
            "Alias collection contains records for another item."
        }

        val activeNormalizedAliases = aliases
            .filter { it.deletedAt == null }
            .map(ItemAlias::normalizedAlias)
        require(activeNormalizedAliases.size == activeNormalizedAliases.toSet().size) {
            "Item contains duplicate active normalized aliases."
        }
    }

    /**
     * 校验物品照片归属、封面唯一性和展示顺序唯一性。
     *
     * @param item 照片所属物品。
     * @param photos 可包含软删除记录的照片集合。
     */
    fun validatePhotoCollection(
        item: Item,
        photos: Collection<PhotoAsset>,
    ) {
        require(photos.all { photo ->
            photo.itemId == item.id && photo.householdId == item.householdId
        }) {
            "Photo collection contains records for another item or household."
        }

        val activePhotos = photos.filter { it.deletedAt == null }
        if (activePhotos.isNotEmpty()) {
            require(activePhotos.count(PhotoAsset::isCover) == 1) {
                "Item with active photos must have exactly one active cover."
            }
        }

        val activeSortOrders = activePhotos.map { it.sortOrder.value }
        require(activeSortOrders.size == activeSortOrders.toSet().size) {
            "Item contains duplicate active photo sort orders."
        }
    }

    /**
     * 从指定位置沿父链向上遍历，确保不会再次访问同一节点。
     */
    private fun validateNoLocationCycle(
        startLocation: LocationNode,
        locationsById: Map<LocationNodeId, LocationNode>,
    ) {
        val visitedLocationIds = mutableSetOf<LocationNodeId>()
        var currentLocation: LocationNode? = startLocation

        while (currentLocation != null) {
            require(visitedLocationIds.add(currentLocation.id)) {
                "Location tree must not contain a cycle."
            }
            currentLocation = currentLocation.parentId?.let(locationsById::get)
        }
    }
}
