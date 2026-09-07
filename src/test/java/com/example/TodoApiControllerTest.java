package com.example;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TodoApiController.class)
@Import(TodoService.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TodoApiControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@Order(1)
	void getAllTodosReturnsListOfTodos() throws Exception {
		mockMvc.perform(get("/api/v1/todos"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value("1"))
				.andExpect(jsonPath("$[0].title").value("Sample Todo 1"))
				.andExpect(jsonPath("$[0].completed").value(false));
	}

	@Test
	@Order(2)
	void getTodoByIdReturnsOk() throws Exception {
		mockMvc.perform(get("/api/v1/todos/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("1"))
				.andExpect(jsonPath("$.title").value("Sample Todo 1"));
	}

	@Test
	@Order(3)
	void getTodoByIdReturns404ForMissing() throws Exception {
		mockMvc.perform(get("/api/v1/todos/999"))
				.andExpect(status().isNotFound());
	}

	@Test
	@Order(4)
	void getTodosReturnsJson() throws Exception {
		mockMvc.perform(get("/api/v1/todos"))
				.andExpect(status().isOk())
				.andExpect(content().contentType("application/json"));
	}

	@Test
	@Order(5)
	void createTodoReturns201() throws Exception {
		mockMvc.perform(post("/api/v1/todos")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\": \"New Todo\", \"completed\": false}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("New Todo"))
				.andExpect(jsonPath("$.id").isNotEmpty());
	}

	@Test
	@Order(6)
	void updateTodoReturnsOk() throws Exception {
		mockMvc.perform(put("/api/v1/todos/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\": \"Updated Todo\", \"completed\": true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Updated Todo"))
				.andExpect(jsonPath("$.completed").value(true));
	}

	@Test
	@Order(7)
	void updateTodoReturns404ForMissing() throws Exception {
		mockMvc.perform(put("/api/v1/todos/999")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\": \"Updated\", \"completed\": false}"))
				.andExpect(status().isNotFound());
	}

	@Test
	@Order(8)
	void deleteTodoReturns404ForMissing() throws Exception {
		mockMvc.perform(delete("/api/v1/todos/999"))
				.andExpect(status().isNotFound());
	}

	@Test
	@Order(9)
	void deleteTodoReturns204() throws Exception {
		mockMvc.perform(delete("/api/v1/todos/1"))
				.andExpect(status().isNoContent());
	}
}
