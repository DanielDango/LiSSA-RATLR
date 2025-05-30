# Caching System

## Overview

LiSSA implements a sophisticated caching system to improve performance and ensure reproducibility of results. The caching system consists of the following components:

1. **Cache Interface** (`cache` package)
   - [`Cache`](../src/main/java/edu/kit/kastel/sdq/lissa/ratlr/cache/Cache.java): Core interface defining cache operations
   - [`CacheKey`](../src/main/java/edu/kit/kastel/sdq/lissa/ratlr/cache/CacheKey.java): Represents a unique key for cached items, including model name, seed, mode (EMBEDDING/CHAT), and content
2. **Cache Implementations**
   - [`LocalCache`](../src/main/java/edu/kit/kastel/sdq/lissa/ratlr/cache/LocalCache.java): File-based cache implementation that stores data in JSON format
     - Implements dirty tracking to optimize writes
     - Automatically saves changes on shutdown
     - Supports atomic writes using temporary files
   - [`RedisCache`](../src/main/java/edu/kit/kastel/sdq/lissa/ratlr/cache/RedisCache.java): Redis-based cache implementation with fallback to local cache
     - Uses Redis for high-performance caching
     - Falls back to local cache if Redis is unavailable
     - Supports both string and object serialization
3. **Cache Management**
   - [`CacheManager`](../src/main/java/edu/kit/kastel/sdq/lissa/ratlr/cache/CacheManager.java): Central manager for cache instances
     - Manages cache directory configuration
     - Provides singleton access to cache instances
     - Handles cache creation and retrieval
4. **Caching Usage**
   The caching system is used in several key components:
   - **Embedding Creators**: Caches vector embeddings to avoid recalculating them
   - **Classifiers**: Caches LLM responses for classification tasks
   - **Preprocessors**: Caches preprocessing results for text summarization and other operations
5. **Configuration**

   ```json
   {
     "cache_dir": "./cache/path"  // Directory for cache storage
   }
   ```
6. **Redis Setup**
   To use Redis for caching, you need to set up a Redis server. Here's a recommended Docker Compose configuration:

   ```yaml
   services:
     redis:
       image: redis/redis-stack:latest
       container_name: redis
       restart: unless-stopped
       ports:
         - "127.0.0.1:6379:6379"  # Redis server port
         - "127.0.0.1:5540:8001"  # RedisInsight web interface
       volumes:
         - ./redis_data:/data     # Persistent storage
   ```

   The Redis server will be available at `redis://localhost:6379`. You can also access the RedisInsight web interface at `http://localhost:5540` for monitoring and management.

   To use Redis with LiSSA:
   1. Start the Redis server using Docker Compose
   2. The system will automatically use Redis if available
   3. If Redis is unavailable, it will fall back to local file-based caching

7. **Best Practices**

   - Use the cache directory specified in the configuration
   - Clear the cache directory if you encounter issues
   - For production environments:
     - Use Redis for better performance
     - Configure Redis persistence for data durability
     - Monitor Redis memory usage
     - Set up Redis replication for high availability
   - Monitor cache size and implement cleanup strategies if needed

