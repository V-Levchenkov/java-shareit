package ru.practicum.shareit.item.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import ru.practicum.shareit.booking.dto.BookingDtoForItem;
import ru.practicum.shareit.booking.dto.BookingMapper;
import ru.practicum.shareit.booking.enums.Status;
import ru.practicum.shareit.booking.model.Booking;
import ru.practicum.shareit.booking.repository.BookingRepository;
import ru.practicum.shareit.exception.BookingException;
import ru.practicum.shareit.exception.StorageException;
import ru.practicum.shareit.item.dto.*;
import ru.practicum.shareit.item.model.Comment;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.item.repository.CommentRepository;
import ru.practicum.shareit.item.repository.ItemRepository;
import ru.practicum.shareit.requests.repository.ItemRequestRepository;
import ru.practicum.shareit.user.model.User;
import ru.practicum.shareit.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ItemServiceImpl implements ItemService {

    private final ItemRepository itemRepository;
    private final ItemMapper itemMapper;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final CommentRepository commentRepository;
    private final CommentMapper commentMapper;
    private final ItemRequestRepository itemRequestRepository;

    @Autowired
    public ItemServiceImpl(ItemRepository itemRepository, ItemMapper itemMapper,
                           UserRepository userRepository, BookingRepository bookingRepository,
                           BookingMapper bookingMapper, CommentRepository commentRepository,
                           CommentMapper commentMapper, ItemRequestRepository itemRequestRepository) {
        this.itemRepository = itemRepository;
        this.itemMapper = itemMapper;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.bookingMapper = bookingMapper;
        this.commentRepository = commentRepository;
        this.commentMapper = commentMapper;
        this.itemRequestRepository = itemRequestRepository;
    }

    @Override
    public ItemDtoWithBooking findById(long itemId, long userId) {
        log.info("Запрошен поиск по itemId: {}", itemId);
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new StorageException("Вещи с Id = " + itemId + " нет в БД"));
        ItemDtoWithBooking itemDtoWithBooking = itemMapper
                .toItemDtoWithBooking(item);
        if (item.getOwner().getId() == userId) {
            createItemDtoWithBooking(itemDtoWithBooking);
        }
        List<Comment> comments = commentRepository.findAllByItemId(itemId);
        if (!comments.isEmpty()) {
            itemDtoWithBooking.setComments(comments
                    .stream().map(commentMapper::toCommentDto)
                    .collect(Collectors.toList()));
        }
        return itemDtoWithBooking;
    }

    @Override
    public List<ItemDtoWithBooking> findAll(long userId, int from, int size) {
        log.info("Запрошен поиск item по userId: {}", userId);
        int page = from / size;
        Pageable pageable = PageRequest.of(page, size);
        List<ItemDtoWithBooking> result = itemRepository.findByOwnerId(userId, pageable).stream()
                .map(itemMapper::toItemDtoWithBooking)
                .collect(Collectors.toList());
        for (ItemDtoWithBooking itemDtoWithBooking : result) {
            createItemDtoWithBooking(itemDtoWithBooking);
            List<Comment> comments = commentRepository.findAllByItemId(itemDtoWithBooking.getId());
            if (!comments.isEmpty()) {
                itemDtoWithBooking.setComments(comments
                        .stream().map(commentMapper::toCommentDto)
                        .collect(Collectors.toList()));
            }
        }
        result.sort(Comparator.comparing(ItemDtoWithBooking::getId));
        return result;
    }

    private void createItemDtoWithBooking(ItemDtoWithBooking itemDtoWithBooking) {
        List<Booking> lastBookings = bookingRepository
                .findBookingsByItemIdAndEndIsBeforeOrderByEndDesc(itemDtoWithBooking.getId(),
                        LocalDateTime.now());
        if (!lastBookings.isEmpty()) {
            BookingDtoForItem lastBooking = bookingMapper.toBookingDtoForItem(lastBookings.get(0));
            itemDtoWithBooking.setLastBooking(lastBooking);
        }
        List<Booking> nextBookings = bookingRepository
                .findBookingsByItemIdAndStartIsAfterOrderByStartDesc(itemDtoWithBooking.getId(),
                        LocalDateTime.now());
        if (!nextBookings.isEmpty()) {
            BookingDtoForItem nextBooking = bookingMapper.toBookingDtoForItem(nextBookings.get(0));
            itemDtoWithBooking.setNextBooking(nextBooking);
        }
    }

    @Override
    public ItemDto save(long userId, ItemDto itemDto) {
        log.info("Запрошен метод сохранения item для userId: {}", userId);
        Item item = itemMapper.toItem(itemDto);
        item.setOwner(userRepository.findById(userId)
                .orElseThrow(() -> new StorageException("Incorrect userId")));
        Long requestId = itemDto.getRequestId();
        if (requestId != null) {
            item.setItemRequest(itemRequestRepository.findById(requestId)
                    .orElseThrow(() -> new StorageException("Incorrect RequestId")));
        }
        return itemMapper.toItemDto(itemRepository.save(item));
    }

    @Override
    public CommentDto saveComment(long userId, long itemId, CommentDto commentDto) {
        log.info("Запрошен метод сохранения comment для вещи: {}", itemId);
        Item item = itemRepository.findById(itemId).orElseThrow(() ->
                new StorageException("Вещи с Id = " + itemId + " нет в БД"));
        User user = userRepository.findById(userId).orElseThrow(() ->
                new StorageException("Пользователя с Id = " + userId + " нет в БД"));
        List<Booking> bookings = bookingRepository
                .searchBookingByBookerIdAndItemIdAndEndIsBeforeAndStatus(userId, itemId,
                        LocalDateTime.now(), Status.APPROVED);
        if (bookings.isEmpty()) {
            throw new BookingException("Пользователь с Id = " + userId + " не брал в аренду вещь с Id = " + itemId);
        }
        Comment comment = commentMapper.toComment(commentDto);
        comment.setItem(item);
        comment.setAuthor(user);
        commentRepository.save(comment);
        return commentMapper.toCommentDto(comment);
    }

    @Override
    public ItemDto update(long userId, long id, ItemDto itemDto) {
        log.info("Запрошен метод update ItemId: {}", id);
        try {
            Item oldItem = itemRepository.findById(id).orElseThrow();

            if (oldItem.getOwner().getId() == userId) {

                if (itemDto.getName() != null) {
                    oldItem.setName(itemDto.getName());
                }
                if (itemDto.getDescription() != null) {
                    oldItem.setDescription(itemDto.getDescription());
                }
                if (itemDto.getAvailable() != null) {
                    oldItem.setAvailable(itemDto.getAvailable());
                }
                return itemMapper.toItemDto(itemRepository.save(oldItem));
            } else {
                throw new StorageException("Incorrect userId");
            }
        } catch (Exception e) {
            throw new StorageException("Incorrect ItemId");
        }
    }

    @Override
    public void deleteById(long itemId) {
        log.info("Запрошен метод удаления item по id: {}", itemId);
        itemRepository.deleteById(itemId);
    }

    @Override
    public List<ItemDto> searchItem(String text, int from, int size) {
        log.info("Запрошен метод поиска item searchItem: {}", text);
        int page = from / size;
        Pageable pageable = PageRequest.of(page, size);
        if (!text.isBlank()) {
            return itemRepository.search(text, pageable)
                    .stream()
                    .filter(Item::getAvailable)
                    .map(itemMapper::toItemDto)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
