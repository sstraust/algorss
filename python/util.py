from dateutil import parser

def catch_all_exceptions(func):
    """
    Decorator to catch all exceptions in a function and return None.
    
    :param func: The function to wrap
    :return: A wrapped function that catches exceptions
    """
    def wrapper(*args, **kwargs):
        try:
            return func(*args, **kwargs)
        except Exception as e:
            # Optionally log or print the exception for debugging
            print(f"Exception in {func.__name__}: {e}")
            return None
    return wrapper

def parsedate(inp):
    try:
        if inp:
            return parser.parse(inp)
        else:
            return None
    except Exception as e:
        print(e)
        return None
