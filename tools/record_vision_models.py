"""Attach matching official input metadata to pinned legacy lite model weights."""
import io
import zipfile


def lite_with_metadata(lite, standard):
    try:
        from mediapipe.tasks.python.metadata import metadata
        from mediapipe.tasks.metadata import schema_py_generated as schema
    except ImportError as error:
        raise RuntimeError('Install tools/vision-build-requirements.txt to build lite models') from error

    def model(data):
        return schema.Model.GetRootAsModel(data, 0)

    def interface(data):
        graph = model(data).Subgraphs(0)
        def tensors(indices):
            return [(graph.Tensors(i).Type(), tuple(graph.Tensors(i).ShapeAsNumpy())) for i in indices]
        return tensors(graph.InputsAsNumpy()), tensors(graph.OutputsAsNumpy())

    if interface(lite) != interface(standard):
        raise ValueError('Lite and metadata reference model tensor interfaces differ')
    display = metadata.MetadataDisplayer.with_model_buffer(standard)
    labels = {name: display.get_associated_file_buffer(name) for name in display.get_packed_associated_file_list()}
    populator = metadata.MetadataPopulator.with_model_buffer(lite)
    populator.load_metadata_buffer(metadata.get_metadata_buffer(standard))
    populator.load_associated_file_buffers(labels)
    populator.populate()
    populated = bytes(populator.get_model_buffer())

    # Metadata tools append label ZIP entries with current dates; normalize them
    # so both the derived models and final task bundle have reproducible hashes.
    with zipfile.ZipFile(io.BytesIO(populated)) as archive:
        files = {name: archive.read(name) for name in sorted(archive.namelist())}
        prefix = populated[:min(entry.header_offset for entry in archive.infolist())]
    output = io.BytesIO(prefix)
    with zipfile.ZipFile(output, 'a', compression=zipfile.ZIP_STORED) as archive:
        for name, data in files.items():
            entry = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            entry.external_attr = 0o644 << 16
            archive.writestr(entry, data)
    result = output.getvalue()
    if interface(result) != interface(lite):
        raise ValueError('Metadata population changed the tensor interface')
    before, after = model(lite), model(result)
    # Every tensor weight buffer must remain byte-for-byte identical.
    for graph_index in range(before.SubgraphsLength()):
        graph = before.Subgraphs(graph_index)
        for tensor_index in range(graph.TensorsLength()):
            buffer_index = graph.Tensors(tensor_index).Buffer()
            def contents(value):
                buffer = value.Buffers(buffer_index)
                return b'' if buffer.DataLength() == 0 else buffer.DataAsNumpy().tobytes()
            if contents(before) != contents(after):
                raise ValueError('Metadata population changed model weights')
    return result
