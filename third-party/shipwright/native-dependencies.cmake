include(FetchContent)
include("${CMAKE_CURRENT_LIST_DIR}/sources.cmake")
set(CMAKE_TLS_VERIFY ON)
set(FETCHCONTENT_TRY_FIND_PACKAGE_MODE NEVER)

foreach(name yaml-cpp tinyxml2 zlib)
    string(TOUPPER "${name}" key)
    FetchContent_Declare(${name}
        URL "${ZOOT_${key}_URL}"
        URL_HASH "SHA256=${ZOOT_${key}_HASH}"
        DOWNLOAD_EXTRACT_TIMESTAMP FALSE
    )
endforeach()

# Use one known logging implementation, independent of Homebrew packages.
FetchContent_Declare(spdlog
    URL "${ZOOT_SPDLOG_URL}"
    URL_HASH "SHA256=${ZOOT_SPDLOG_HASH}"
    DOWNLOAD_EXTRACT_TIMESTAMP FALSE
    OVERRIDE_FIND_PACKAGE
)
set(SPDLOG_FMT_EXTERNAL OFF CACHE BOOL "" FORCE)
set(SPDLOG_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(SPDLOG_BUILD_EXAMPLE OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(spdlog)
