cmake_minimum_required(VERSION 3.26)
project(ZootSourceDependencies LANGUAGES NONE)

include(FetchContent)
include(sources.cmake)
set(CMAKE_TLS_VERIFY ON)
set(FETCHCONTENT_TRY_FIND_PACKAGE_MODE NEVER)

foreach(name shipwright libultraship torch)
    string(TOUPPER "${name}" key)
    FetchContent_Declare(${name}
        URL "${ZOOT_${key}_URL}"
        URL_HASH "SHA256=${ZOOT_${key}_HASH}"
        SOURCE_DIR "${CMAKE_BINARY_DIR}/upstream/${name}"
        SOURCE_SUBDIR zoot-source-only
        DOWNLOAD_EXTRACT_TIMESTAMP FALSE
    )
    FetchContent_MakeAvailable(${name})
endforeach()
