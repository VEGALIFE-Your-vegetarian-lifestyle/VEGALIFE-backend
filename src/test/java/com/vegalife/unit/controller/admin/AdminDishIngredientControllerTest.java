package com.vegalife.unit.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.controller.admin.AdminDishIngredientController;
import com.vegalife.dto.request.admin.AdminDishIngredientCreateRequest;
import com.vegalife.dto.request.admin.AdminDishIngredientUpdateRequest;
import com.vegalife.dto.response.admin.AdminDishIngredientResponse;
import com.vegalife.service.admin.AdminDishIngredientService;
import com.vegalife.shared.dto.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AdminDishIngredientControllerTest {

  @Mock private AdminDishIngredientService adminDishIngredientService;

  @InjectMocks private AdminDishIngredientController adminDishIngredientController;

  private final UUID adminId = UUID.randomUUID();
  private final UUID dishId = UUID.randomUUID();
  private final UUID ingredientId = UUID.randomUUID();

  @Test
  void listDishIngredientsReturns200() {
    List<AdminDishIngredientResponse> entries = List.of(entry());
    when(adminDishIngredientService.listDishIngredients(dishId)).thenReturn(entries);

    ResponseEntity<ApiResponse<List<AdminDishIngredientResponse>>> result =
        adminDishIngredientController.listDishIngredients(dishId);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody().getData()).isEqualTo(entries);
  }

  @Test
  void addDishIngredientNewEntryReturns201() {
    AdminDishIngredientCreateRequest request =
        AdminDishIngredientCreateRequest.builder().ingredientName("tofu").build();
    when(adminDishIngredientService.addDishIngredient(adminId, dishId, request))
        .thenReturn(new AdminDishIngredientService.AddResult(entry(), true));

    ResponseEntity<ApiResponse<AdminDishIngredientResponse>> result =
        adminDishIngredientController.addDishIngredient(adminId, dishId, request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(result.getBody().getMessage()).isEqualTo("Dish ingredient added successfully");
    verify(adminDishIngredientService).addDishIngredient(adminId, dishId, request);
  }

  @Test
  void addDishIngredientReinstatedEntryReturns200() {
    AdminDishIngredientCreateRequest request =
        AdminDishIngredientCreateRequest.builder().ingredientName("tofu").build();
    when(adminDishIngredientService.addDishIngredient(adminId, dishId, request))
        .thenReturn(new AdminDishIngredientService.AddResult(entry(), false));

    ResponseEntity<ApiResponse<AdminDishIngredientResponse>> result =
        adminDishIngredientController.addDishIngredient(adminId, dishId, request);

    // Reinstating a withdrawn entry is not a creation: the row was only hidden.
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody().getMessage()).isEqualTo("Dish ingredient restored successfully");
  }

  @Test
  void updateDishIngredientReturns200() {
    AdminDishIngredientUpdateRequest request =
        AdminDishIngredientUpdateRequest.builder().required(true).build();
    when(adminDishIngredientService.updateDishIngredient(adminId, dishId, ingredientId, request))
        .thenReturn(entry());

    ResponseEntity<ApiResponse<AdminDishIngredientResponse>> result =
        adminDishIngredientController.updateDishIngredient(adminId, dishId, ingredientId, request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(adminDishIngredientService).updateDishIngredient(adminId, dishId, ingredientId, request);
  }

  @Test
  void removeDishIngredientReturns200() {
    ResponseEntity<ApiResponse<Void>> result =
        adminDishIngredientController.removeDishIngredient(adminId, dishId, ingredientId);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody().getMessage()).isEqualTo("Dish ingredient removed successfully");
    verify(adminDishIngredientService).removeDishIngredient(adminId, dishId, ingredientId);
  }

  private AdminDishIngredientResponse entry() {
    return AdminDishIngredientResponse.builder()
        .ingredientId(ingredientId)
        .name("tofu")
        .moderationStatus("APPROVED")
        .required(false)
        .build();
  }
}
