package ru.practicum.shareit.item;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import ru.practicum.shareit.item.dto.CommentDto;
import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.item.service.ItemService;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/items")
public class ItemController {
    private final ItemService itemService;

    @Autowired
    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @PostMapping()
    ItemDto create(@NotBlank @RequestHeader("X-Sharer-User-Id") long userId,
                   @RequestBody @Valid ItemDto itemDto) {
        log.info("create item");
        return itemService.create(userId, itemDto);
    }

    @PatchMapping("/{itemId}")
    ItemDto update(@NotBlank @RequestHeader("X-Sharer-User-Id") Long userId,
                   @PathVariable Long itemId,
                   @RequestBody ItemDto itemDto) {
        log.info("update item id={}", itemId);
        return itemService.update(userId, itemId, itemDto);
    }

    @GetMapping("/{itemId}")
    ItemDto getItemById(@NotBlank @RequestHeader("X-Sharer-User-Id") Long userId,
                        @PathVariable Long itemId) {
        log.info("get item id={}", itemId);
        return itemService.getItemById(userId, itemId);
    }

    @GetMapping()
    List<ItemDto> getAllItemsByUser(@NotBlank @RequestHeader("X-Sharer-User-Id") Long userId) {
        log.info("get all items from user id={}", userId);
        return itemService.getAllItemsByUser(userId);

    }

    @GetMapping("search")
    List<ItemDto> search(@RequestParam(required = false) String text) {
        log.info("search text={}", text);
        return itemService.search(text)
                .stream()
                .map(ItemMapper::toItemDto)
                .collect(Collectors.toList());
    }

    @PostMapping("/{itemId}/comment")
    CommentDto createComment(@NotBlank @RequestHeader("X-Sharer-User-Id") Long userId,
                             @PathVariable long itemId,
                             @RequestBody @Valid CommentDto commentDto) {
        log.info("create comment");
        return itemService.createComment(userId, itemId, commentDto);
    }
}

