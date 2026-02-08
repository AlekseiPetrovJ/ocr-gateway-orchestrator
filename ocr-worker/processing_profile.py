from enum import Enum

class ProcessingProfile(str, Enum):
    parse = "parse"
    clean_parse = "clean_parse"
    norm_parse = "norm_parse"
    full = "clean_norm_parse"
