package com.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class TodoService {

    private final List<Todo> todos = new ArrayList<>(List.of(
        new Todo("1", "Sample Todo 1", false),
        new Todo("2", "Sample Todo 2", true)
    ));

    public List<Todo> getAllTodos() {
        return List.copyOf(todos);
    }

    public Optional<Todo> getTodoById(String id) {
        return todos.stream()
                .filter(todo -> todo.getId().equals(id))
                .findFirst();
    }

    public Todo createTodo(Todo todo) {
        todo.setId(UUID.randomUUID().toString());
        todos.add(todo);
        return todo;
    }

    public Optional<Todo> updateTodo(String id, Todo updated) {
        return getTodoById(id).map(existing -> {
            existing.setTitle(updated.getTitle());
            existing.setCompleted(updated.isCompleted());
            return existing;
        });
    }

    public boolean deleteTodo(String id) {
        return todos.removeIf(todo -> todo.getId().equals(id));
    }
}
