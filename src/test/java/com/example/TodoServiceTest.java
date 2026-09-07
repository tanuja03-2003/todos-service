package com.example;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TodoServiceTest {

	private TodoService todoService;

	@BeforeEach
	void setUp() {
		todoService = new TodoService();
	}

	@Test
	void getAllTodosReturnsInitialTodos() {
		List<Todo> todos = todoService.getAllTodos();
		assertEquals(2, todos.size());
		assertEquals("Sample Todo 1", todos.get(0).getTitle());
	}

	@Test
	void getTodoByIdReturnsExistingTodo() {
		Optional<Todo> todo = todoService.getTodoById("1");
		assertTrue(todo.isPresent());
		assertEquals("Sample Todo 1", todo.get().getTitle());
	}

	@Test
	void getTodoByIdReturnsEmptyForMissing() {
		Optional<Todo> todo = todoService.getTodoById("999");
		assertTrue(todo.isEmpty());
	}

	@Test
	void createTodoAssignsIdAndAdds() {
		Todo newTodo = new Todo(null, "New Todo", false);
		Todo created = todoService.createTodo(newTodo);

		assertNotNull(created.getId());
		assertEquals("New Todo", created.getTitle());
		assertEquals(3, todoService.getAllTodos().size());
	}

	@Test
	void updateTodoModifiesExisting() {
		Todo updated = new Todo(null, "Updated Title", true);
		Optional<Todo> result = todoService.updateTodo("1", updated);

		assertTrue(result.isPresent());
		assertEquals("Updated Title", result.get().getTitle());
		assertTrue(result.get().isCompleted());
	}

	@Test
	void updateTodoReturnsEmptyForMissing() {
		Todo updated = new Todo(null, "Updated", false);
		Optional<Todo> result = todoService.updateTodo("999", updated);
		assertTrue(result.isEmpty());
	}

	@Test
	void deleteTodoRemovesExisting() {
		assertTrue(todoService.deleteTodo("1"));
		assertEquals(1, todoService.getAllTodos().size());
	}

	@Test
	void deleteTodoReturnsFalseForMissing() {
		assertFalse(todoService.deleteTodo("999"));
	}
}
