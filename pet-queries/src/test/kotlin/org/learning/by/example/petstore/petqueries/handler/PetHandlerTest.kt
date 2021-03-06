package org.learning.by.example.petstore.petqueries.handler

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.learning.by.example.petstore.petqueries.model.ErrorResponse
import org.learning.by.example.petstore.petqueries.model.Pet
import org.learning.by.example.petstore.petqueries.service.PetService
import org.learning.by.example.petstore.petqueries.service.PetServiceException
import org.learning.by.example.petstore.petqueries.testing.verify
import org.learning.by.example.petstore.petqueries.testing.verifyEmpty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.reactive.function.server.HandlerStrategies
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerRequest
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.util.Collections
import java.util.UUID

@SpringBootTest
internal class PetHandlerTest(@Autowired private val petHandler: PetHandler) {
    @SpyBean
    private lateinit var petService: PetService

    companion object {
        const val PET_GET_PATH = "/pet"
        const val EXISTING_PET_ID = "4cb5294b-1034-4bc4-9b3d-542adb232a21"
        const val NOT_EXISTING_PET_ID = "111ccc4b-1034-4bc4-9b3d-542adb232a21"
        const val CONTROLLED_ERROR_PET_ID = "111ccc4b-1034-abcd-9b3d-542adb232a21"
        const val UNCONTROLLED_ERROR_PET_ID = "111ccc4b-1034-abcd-bbbb-542adb232a21"
    }

    @BeforeEach
    fun setup() {
        doReturn(
            Mono.empty<Pet>()
        ).whenever(petService).findPetById(any())
        doReturn(
            Pet(
                "fluffy", "dog", "german shepherd", "2020-06-28T00:00:00.0Z",
                listOf("vaccine1", "vaccine2", "vaccine3")
            ).toMono()
        ).whenever(petService).findPetById(UUID.fromString(EXISTING_PET_ID))
        doReturn(
            Mono.error<Pet>(PetServiceException("Error getting pet", RuntimeException("error")))
        ).whenever(petService).findPetById(UUID.fromString(CONTROLLED_ERROR_PET_ID))
        doReturn(
            Mono.error<Pet>(RuntimeException("error"))
        ).whenever(petService).findPetById(UUID.fromString(UNCONTROLLED_ERROR_PET_ID))
    }

    @AfterEach
    fun tearDown() {
        reset(petService)
    }

    @Test
    fun `when ask to get a pet it should return the pet details when the pet exists`() {
        val httpRequest = MockServerHttpRequest
            .get(PET_GET_PATH)
            .accept(MediaType.APPLICATION_JSON)
        val webExchange = MockServerWebExchange.from(httpRequest)
        val pathVariables = Collections.singletonMap("id", EXISTING_PET_ID)
        webExchange.attributes[RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE] = pathVariables

        val request = ServerRequest.create(webExchange, HandlerStrategies.withDefaults().messageReaders())
        petHandler.getPet(request).verify { response, pet: Pet ->
            assertThat(response.statusCode()).isEqualTo(HttpStatus.OK)
            assertThat(response.headers().contentType).isEqualTo(MediaType.APPLICATION_JSON)

            assertThat(pet.name).isEqualTo("fluffy")
            assertThat(pet.category).isEqualTo("dog")
            assertThat(pet.breed).isEqualTo("german shepherd")
            assertThat(pet.dob).isEqualTo("2020-06-28T00:00:00.0Z")
            assertThat(pet.vaccines).hasSize(3)
            assertThat(pet.vaccines).containsAll(listOf("vaccine1", "vaccine2", "vaccine3"))
        }

        verify(petService).findPetById(UUID.fromString(EXISTING_PET_ID))
        verifyNoMoreInteractions(petService)
    }

    @Test
    fun `when ask to get a pet it should return not found when the pet does not exists`() {
        val httpRequest = MockServerHttpRequest
            .get(PET_GET_PATH)
            .accept(MediaType.APPLICATION_JSON)
        val webExchange = MockServerWebExchange.from(httpRequest)
        val pathVariables = Collections.singletonMap("id", NOT_EXISTING_PET_ID)
        webExchange.attributes[RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE] = pathVariables

        val request = ServerRequest.create(webExchange, HandlerStrategies.withDefaults().messageReaders())
        petHandler.getPet(request).verifyEmpty { response ->
            assertThat(response.statusCode()).isEqualTo(HttpStatus.NOT_FOUND)
        }

        verify(petService).findPetById(UUID.fromString(NOT_EXISTING_PET_ID))
        verifyNoMoreInteractions(petService)
    }

    data class Expect(val description: String, val moreInfo: String?)
    data class Params(val petId: String)
    data class TestCase(val name: String, val params: Params, val expect: Expect)

    @TestFactory
    fun `when ask to get a pet `() = listOf(
        TestCase(
            name = "it should return an error when the service returns a controlled error",
            params = Params(CONTROLLED_ERROR_PET_ID),
            expect = Expect("Pet cannot be retrieved", "Error getting pet")
        ),
        TestCase(
            name = "it should return an error when the service returns a uncontrolled error",
            params = Params(UNCONTROLLED_ERROR_PET_ID),
            expect = Expect("Pet cannot be retrieved", "Unknown error")
        )
    ).map {
        DynamicTest.dynamicTest(it.name) {
            val httpRequest = MockServerHttpRequest
                .get(PET_GET_PATH)
                .accept(MediaType.APPLICATION_JSON)
            val webExchange = MockServerWebExchange.from(httpRequest)
            val pathVariables = Collections.singletonMap("id", it.params.petId)
            webExchange.attributes[RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE] = pathVariables

            val request = ServerRequest.create(webExchange, HandlerStrategies.withDefaults().messageReaders())
            petHandler.getPet(request).verify { response, error: ErrorResponse ->
                assertThat(response.statusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
                assertThat(error.description).isEqualTo(it.expect.description)
                assertThat(error.moreInfo).isEqualTo(it.expect.moreInfo)
            }

            verify(petService).findPetById(UUID.fromString(it.params.petId))
            verifyNoMoreInteractions(petService)
        }
    }
}
