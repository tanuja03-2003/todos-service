package com.example;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TodoTest {

	@Test
	void testConstructorAndGetters() {
		Todo todo = new Todo("1", "Buy groceries", false);
		assertEquals("1", todo.getId());
		assertEquals("Buy groceries", todo.getTitle());
		assertFalse(todo.isCompleted());
	}

	@Test
	void testSetId() {
		Todo todo = new Todo("1", "Task", false);
		todo.setId("2");
		assertEquals("2", todo.getId());
	}

	@Test
	void testSetTitle() {
		Todo todo = new Todo("1", "Old Title", false);
		todo.setTitle("New Title");
		assertEquals("New Title", todo.getTitle());
	}

	@Test
	void testSetCompleted() {
		Todo todo = new Todo("1", "Task", false);
		todo.setCompleted(true);
		assertTrue(todo.isCompleted());
	}
}
