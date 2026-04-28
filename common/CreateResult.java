package common;

// Generic wrapper
public record CreateResult<T>(T value, boolean created) {
    
}
