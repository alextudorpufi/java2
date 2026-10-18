package com.example.library;

import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Repository
public class BookRepository {
    private final Map<Long, Book> books = new ConcurrentHashMap<>();

    public void save(Book book) { books.put(book.id(), book); }
    public Optional<Book> findById(long id) { return Optional.ofNullable(books.get(id)); }
    public List<Book> findAll() { return List.copyOf(books.values()); }
}
