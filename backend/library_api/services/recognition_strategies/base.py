class RecognitionProviderError(RuntimeError):
    """Raised when an external recognition provider cannot serve a request."""


class RecognitionStrategy:
    name = "base"
    is_primary = False

    def recognize(self, uploaded_files, temp_paths):
        raise NotImplementedError
