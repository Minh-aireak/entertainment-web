package com.MyProject.post.post_service.repository;

import com.MyProject.post.post_service.entity.TravelItinerary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TravelItineraryRepository extends MongoRepository<TravelItinerary, String> {
    @Query(" {'_class': 'travel-itinerary', 'userId': ?0 }")
    Page<TravelItinerary> findAllByUserId(String userId, Pageable pageable);

    @Query("{ 'status': { $in: ?0 } }")
    List<TravelItinerary> findAllByStatusValid(List<String> validStatus);

    @Query(value = "{'_class': 'travel-itinerary', 'id': ?0}")
    Optional<TravelItinerary> findByIdType(String id);

    @Query("{'_class': 'travel-itinerary', 'userId': ?0 }")
    List<TravelItinerary> findAllByUserIdForUpdate(String userId);
}
